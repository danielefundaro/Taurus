package com.fundaro.zodiac.taurus.service.signature;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.springframework.http.HttpStatus;

/**
 * Structure of a document signed with CieSign. The file must be the frozen document followed by an incremental update
 * that adds one signature covering the file up to the last byte; the update may add the signature field and its
 * widget, but must not change what the pages show or what the document does when opened.
 */
final class CieSignPdfInspector {

    private static final Set<String> SUB_FILTERS = Set.of("ETSI.CAdES.detached", "adbe.pkcs7.detached");
    /**
     * Entries of the catalog the signature can add or change: the form with the signature field, its permissions, the
     * validation data, the metadata and the structure tree, none of which changes the content of the pages.
     */
    private static final Set<COSName> CATALOG_CHANGES = Set.of(
        COSName.ACRO_FORM,
        COSName.PERMS,
        COSName.getPDFName("DSS"),
        COSName.EXTENSIONS,
        COSName.METADATA,
        COSName.VERSION,
        COSName.PAGES,
        COSName.STRUCT_TREE_ROOT,
        COSName.MARK_INFO
    );
    private static final Set<COSName> PAGE_CHANGES = Set.of(COSName.ANNOTS, COSName.PARENT);
    private static final int MAX_DEPTH = 256;

    private CieSignPdfInspector() {
    }

    /**
     * The signature of the file and the bytes it covers.
     */
    record SignedPart(byte[] contents, byte[] signedContent) {
    }

    static SignedPart inspect(byte[] frozen, byte[] signed) {
        if (Arrays.equals(signed, frozen)) throw invalidSignature("The file is the frozen document without a signature");
        if (signed.length <= frozen.length || !Arrays.equals(signed, 0, frozen.length, frozen, 0, frozen.length)) {
            throw documentMismatch("The file does not start with the frozen document");
        }
        try (PDDocument original = load(frozen); PDDocument document = loadSigned(signed)) {
            if (document.isEncrypted()) throw documentMismatch("The signed file is encrypted");
            List<PDSignature> before = original.getSignatureDictionaries();
            List<PDSignature> after = document.getSignatureDictionaries();
            if (after.size() != before.size() + 1) throw invalidSignature("The file must add exactly one signature");
            List<PDSignature> covering = after.stream().filter(signature -> coversToEnd(signature, signed.length)).toList();
            // The new signature does not reach the end: the file was changed after it was signed.
            if (covering.size() != 1) throw documentMismatch("No signature covers the whole file");
            PDSignature signature = covering.get(0);
            if (signature.getSubFilter() == null || !SUB_FILTERS.contains(signature.getSubFilter())) {
                throw invalidSignature("Unsupported signature format");
            }
            int[] range = signature.getByteRange();
            if (range[1] < frozen.length) throw invalidSignature("The signature is not in the incremental update");
            byte[] contents = contents(signed, range);
            byte[] parsed = signature.getContents();
            if (parsed == null || !Arrays.equals(parsed, contents)) throw invalidSignature("The signature value is not the one covered by the byte range");
            if (!unchanged(original, document)) throw documentMismatch("The incremental update changes the document");
            byte[] signedContent = new byte[range[1] + range[3]];
            System.arraycopy(signed, 0, signedContent, 0, range[1]);
            System.arraycopy(signed, range[2], signedContent, range[1], range[3]);
            return new SignedPart(contents, signedContent);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read the frozen document", exception);
        }
    }

    private static PDDocument load(byte[] frozen) throws IOException {
        return Loader.loadPDF(frozen);
    }

    private static PDDocument loadSigned(byte[] signed) {
        try {
            return Loader.loadPDF(signed);
        } catch (IOException exception) {
            throw invalidSignature("The signed file cannot be read");
        }
    }

    private static boolean coversToEnd(PDSignature signature, int length) {
        int[] range = signature.getByteRange();
        return range != null
            && range.length == 4
            && range[0] == 0
            && range[1] > 0
            && range[2] > range[1] + 1
            && range[3] >= 0
            && (long) range[2] + range[3] == length;
    }

    /**
     * The signature value in the gap of the byte range, which must hold only the hexadecimal string.
     */
    private static byte[] contents(byte[] signed, int[] range) {
        if (signed[range[1]] != '<' || signed[range[2] - 1] != '>') throw invalidSignature("The byte range does not exclude the signature value only");
        int length = range[2] - range[1] - 2;
        if (length % 2 != 0) throw invalidSignature("Malformed signature value");
        for (int index = range[1] + 1; index < range[2] - 1; index++) {
            if (Character.digit(signed[index], 16) < 0) throw invalidSignature("The byte range does not exclude the signature value only");
        }
        return HexFormat.of().parseHex(new String(signed, range[1] + 1, length, java.nio.charset.StandardCharsets.US_ASCII));
    }

    /**
     * Whether the signed file shows the same pages as the frozen document: same catalog except the entries the
     * signature adds, same pages with the same inherited attributes, and the same annotations plus signature widgets.
     */
    static boolean unchanged(PDDocument original, PDDocument document) {
        if (original.getNumberOfPages() != document.getNumberOfPages()) return false;
        Map<COSBase, Integer> pagesBefore = pages(original);
        Map<COSBase, Integer> pagesAfter = pages(document);
        COSDictionary catalogBefore = original.getDocumentCatalog().getCOSObject();
        COSDictionary catalogAfter = document.getDocumentCatalog().getCOSObject();
        if (!new Walk(pagesBefore, pagesAfter).sameEntries(catalogBefore, catalogAfter, CATALOG_CHANGES, 0)) return false;
        COSDictionary formAfter = catalogAfter.getCOSDictionary(COSName.ACRO_FORM);
        if (formAfter != null && formAfter.containsKey(COSName.XFA)) {
            COSDictionary formBefore = catalogBefore.getCOSDictionary(COSName.ACRO_FORM);
            COSBase xfaBefore = formBefore == null ? null : formBefore.getItem(COSName.XFA);
            if (!new Walk(pagesBefore, pagesAfter).same(xfaBefore, formAfter.getItem(COSName.XFA), 0)) return false;
        }
        for (int index = 0; index < original.getNumberOfPages(); index++) {
            PDPage before = original.getPage(index);
            PDPage after = document.getPage(index);
            if (!new Walk(pagesBefore, pagesAfter).sameEntries(before.getCOSObject(), after.getCOSObject(), PAGE_CHANGES, 0)) return false;
            if (!new Walk(pagesBefore, pagesAfter).same(before.getResources().getCOSObject(), after.getResources().getCOSObject(), 0)) return false;
            if (!sameBox(before.getMediaBox(), after.getMediaBox()) || !sameBox(before.getCropBox(), after.getCropBox())) return false;
            if (before.getRotation() != after.getRotation()) return false;
            if (!sameAnnotations(before, after, pagesBefore, pagesAfter)) return false;
        }
        return true;
    }

    /**
     * The annotations of the frozen page, in the same order, with only signature widgets added.
     */
    private static boolean sameAnnotations(PDPage before, PDPage after, Map<COSBase, Integer> pagesBefore, Map<COSBase, Integer> pagesAfter) {
        List<COSBase> original = annotations(before);
        int next = 0;
        for (COSBase annotation : annotations(after)) {
            if (next < original.size() && new Walk(pagesBefore, pagesAfter).same(original.get(next), annotation, 0)) {
                next++;
            } else if (!signatureWidget(annotation)) {
                return false;
            }
        }
        return next == original.size();
    }

    private static List<COSBase> annotations(PDPage page) {
        List<COSBase> annotations = new ArrayList<>();
        COSArray array = page.getCOSObject().getCOSArray(COSName.ANNOTS);
        if (array == null) return annotations;
        for (int index = 0; index < array.size(); index++) annotations.add(array.getObject(index));
        return annotations;
    }

    /**
     * A widget of a signature field, without actions.
     */
    private static boolean signatureWidget(COSBase value) {
        if (!(value instanceof COSDictionary annotation) || !COSName.WIDGET.equals(annotation.getCOSName(COSName.SUBTYPE))) return false;
        if (annotation.containsKey(COSName.A) || annotation.containsKey(COSName.AA)) return false;
        COSDictionary field = annotation;
        for (int depth = 0; field != null && depth < 32; depth++) {
            COSName type = field.getCOSName(COSName.FT);
            if (type != null) return COSName.SIG.equals(type);
            field = field.getCOSDictionary(COSName.PARENT);
        }
        return false;
    }

    private static boolean sameBox(PDRectangle before, PDRectangle after) {
        if (before == null || after == null) return before == after;
        return Float.compare(before.getLowerLeftX(), after.getLowerLeftX()) == 0
            && Float.compare(before.getLowerLeftY(), after.getLowerLeftY()) == 0
            && Float.compare(before.getUpperRightX(), after.getUpperRightX()) == 0
            && Float.compare(before.getUpperRightY(), after.getUpperRightY()) == 0;
    }

    private static Map<COSBase, Integer> pages(PDDocument document) {
        Map<COSBase, Integer> pages = new IdentityHashMap<>();
        int index = 0;
        for (PDPage page : document.getPages()) pages.put(page.getCOSObject(), index++);
        return pages;
    }

    private static RuntimeException documentMismatch(String message) {
        return SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, message, "signature.cieSign.documentMismatch");
    }

    private static RuntimeException invalidSignature(String message) {
        return SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, message, "signature.cieSign.invalidSignature");
    }

    /**
     * Deep comparison of two objects of the two documents, following the references: pages are equal when they are in
     * the same position, streams when their dictionaries and encoded bytes are equal.
     */
    private static final class Walk {

        private final Map<COSBase, Integer> pagesBefore;
        private final Map<COSBase, Integer> pagesAfter;
        private final Set<Pair> visited = new HashSet<>();

        Walk(Map<COSBase, Integer> pagesBefore, Map<COSBase, Integer> pagesAfter) {
            this.pagesBefore = pagesBefore;
            this.pagesAfter = pagesAfter;
        }

        boolean same(COSBase first, COSBase second, int depth) {
            COSBase before = resolve(first);
            COSBase after = resolve(second);
            if (missing(before) || missing(after)) return missing(before) && missing(after);
            if (depth > MAX_DEPTH) return false;
            Integer pageBefore = pagesBefore.get(before);
            Integer pageAfter = pagesAfter.get(after);
            if (pageBefore != null || pageAfter != null) return Objects.equals(pageBefore, pageAfter);
            // A pair already being compared is assumed equal: the comparison of its other entries decides.
            if (!visited.add(new Pair(before, after))) return true;
            if (before instanceof COSStream streamBefore) {
                return after instanceof COSStream streamAfter && sameEntries(streamBefore, streamAfter, Set.of(), depth) && sameBytes(streamBefore, streamAfter);
            }
            if (before instanceof COSDictionary dictionaryBefore) {
                return after instanceof COSDictionary dictionaryAfter && !(after instanceof COSStream) && sameEntries(dictionaryBefore, dictionaryAfter, Set.of(), depth);
            }
            if (before instanceof COSArray arrayBefore) {
                if (!(after instanceof COSArray arrayAfter) || arrayBefore.size() != arrayAfter.size()) return false;
                for (int index = 0; index < arrayBefore.size(); index++) {
                    if (!same(arrayBefore.get(index), arrayAfter.get(index), depth + 1)) return false;
                }
                return true;
            }
            if (before instanceof COSString stringBefore) return after instanceof COSString stringAfter && Arrays.equals(stringBefore.getBytes(), stringAfter.getBytes());
            return before.equals(after);
        }

        boolean sameEntries(COSDictionary before, COSDictionary after, Set<COSName> excluded, int depth) {
            Set<COSName> keys = new HashSet<>(before.keySet());
            keys.addAll(after.keySet());
            keys.removeAll(excluded);
            for (COSName key : keys) {
                if (!same(before.getItem(key), after.getItem(key), depth + 1)) return false;
            }
            return true;
        }

        private static boolean sameBytes(COSStream before, COSStream after) {
            try (InputStream first = before.createRawInputStream(); InputStream second = after.createRawInputStream()) {
                return Arrays.equals(first.readAllBytes(), second.readAllBytes());
            } catch (IOException exception) {
                return false;
            }
        }

        private static COSBase resolve(COSBase value) {
            COSBase current = value;
            for (int depth = 0; current instanceof COSObject object && depth < 16; depth++) current = object.getObject();
            return current;
        }

        private static boolean missing(COSBase value) {
            return value == null || value instanceof COSNull || value instanceof COSObject;
        }
    }

    /**
     * Two objects compared, by identity.
     */
    private record Pair(COSBase before, COSBase after) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Pair pair && pair.before == before && pair.after == after;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(before) * 31 + System.identityHashCode(after);
        }
    }
}
