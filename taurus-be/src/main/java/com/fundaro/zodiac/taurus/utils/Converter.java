package com.fundaro.zodiac.taurus.utils;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Converter {
    public static String camelCaseToKebabCase(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    public static List<String> pdf2Image(byte[] content, String filename, String destinationPath) throws IOException {
        String formatName = "png";
        int dpi = 300;
        List<String> files = new ArrayList<>();
        filename = filename.replace(" ", "_");

        // Create destination folder
        File destinationFile = new File(Paths.get(destinationPath, filename).toString());

        if (!destinationFile.exists()) {
            destinationFile.mkdirs();
        }

        // Read the pdf file and create images for each page
        try (PDDocument pdDocument = Loader.loadPDF(content)) {
            PDFRenderer pdfRenderer = new PDFRenderer(pdDocument);

            for (int page = 0; page < pdDocument.getNumberOfPages(); ++page) {
                String destinationFilePath = String.format("%s/%s.%s", destinationFile.getPath(), page + 1, formatName);
                BufferedImage bim = pdfRenderer.renderImageWithDPI(page, dpi, ImageType.GRAY);
                ImageIOUtil.writeImage(bim, destinationFilePath, dpi);
                files.add(destinationFilePath);

                // Remove external bounding box
//                BufferedImage img = ImageIO.read(new File(destinationFile));
//                Rectangle bounds = getBounds(bim, Color.WHITE);
//                BufferedImage trimmed = bim.getSubimage(bounds.x, bounds.y, bounds.width, bounds.height);
//                ImageIOUtil.writeImage(trimmed, destinationFile, dpi);
            }
        }

        return files;
    }

    public static byte[] convertObjectToBytes(Object obj) throws IOException {
        ByteArrayOutputStream boas = new ByteArrayOutputStream();
        try (ObjectOutputStream ois = new ObjectOutputStream(boas)) {
            ois.writeObject(obj);
            return boas.toByteArray();
        }
    }

    public static Object convertBytesToObject(byte[] bytes) throws IOException, ClassNotFoundException {
        InputStream is = new ByteArrayInputStream(bytes);
        try (ObjectInputStream ois = new ObjectInputStream(is)) {
            return ois.readObject();
        }
    }

    private static Rectangle getBounds(BufferedImage img, Color fillColor) {
        int width = img.getWidth(), height = img.getHeight();
        int top = height / 2, left = width / 2;
        int bottom = top, right = left;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (new Color(img.getRGB(x, y), true).equals(fillColor)) {
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);

                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
        }

        return new Rectangle(left, top, right - left, bottom - top);
    }
}
