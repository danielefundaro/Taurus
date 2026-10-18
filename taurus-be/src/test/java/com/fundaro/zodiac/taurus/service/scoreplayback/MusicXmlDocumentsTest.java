package com.fundaro.zodiac.taurus.service.scoreplayback;

import com.fundaro.zodiac.taurus.service.scoreplayback.MusicXmlDocuments.MusicXmlException;
import com.fundaro.zodiac.taurus.service.scoreplayback.MusicXmlDocuments.PartInfo;
import com.fundaro.zodiac.taurus.service.scoreplayback.MusicXmlInspector.ScoreChecks;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MusicXmlDocumentsTest {

    static final String TWO_PARTS = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE score-partwise PUBLIC "-//Recordare//DTD MusicXML 4.0 Partwise//EN" "http://www.musicxml.org/dtds/partwise.dtd">
        <score-partwise version="4.0">
          <part-list>
            <score-part id="P1"><part-name>Tuba</part-name></score-part>
            <score-part id="P2"><part-name>Clarinetto</part-name></score-part>
          </part-list>
          <part id="P1">
            <measure number="1">
              <attributes><divisions>2</divisions><key><fifths>-1</fifths></key><time><beats>2</beats><beat-type>4</beat-type></time><clef><sign>F</sign><line>4</line></clef></attributes>
              <direction><direction-type><metronome><beat-unit>quarter</beat-unit><per-minute>50</per-minute></metronome></direction-type><sound tempo="50"/></direction>
              <note><pitch><step>D</step><octave>2</octave></pitch><duration>4</duration><type>half</type></note>
            </measure>
            <measure number="2">
              <barline location="left"><bar-style>heavy-light</bar-style><repeat direction="forward"/></barline>
              <note><pitch><step>A</step><octave>2</octave></pitch><duration>2</duration><type>quarter</type></note>
              <note><pitch><step>G</step><octave>2</octave></pitch><duration>2</duration><type>quarter</type></note>
            </measure>
            <measure number="3">
              <note><pitch><step>D</step><octave>3</octave></pitch><duration>2</duration><type>quarter</type></note>
              <barline location="right"><bar-style>light-heavy</bar-style><repeat direction="backward"/></barline>
            </measure>
          </part>
          <part id="P2">
            <measure number="1">
              <attributes><divisions>1</divisions><time><beats>2</beats><beat-type>4</beat-type></time><clef><sign>G</sign><line>2</line></clef></attributes>
              <note><pitch><step>C</step><octave>5</octave></pitch><duration>1</duration><type>quarter</type></note>
              <note><chord/><pitch><step>E</step><octave>5</octave></pitch><duration>1</duration><type>quarter</type></note>
              <note><rest/><duration>1</duration><type>quarter</type></note>
            </measure>
          </part>
        </score-partwise>
        """;

    @Test
    void readsPlainScoresWithTheirDeclaredDtdWithoutFetchingIt() {
        Document document = MusicXmlDocuments.read(bytes(TWO_PARTS));

        assertThat(MusicXmlDocuments.parts(document)).containsExactly(new PartInfo("P1", "Tuba", 3), new PartInfo("P2", "Clarinetto", 1));
    }

    @Test
    void keepsASinglePartAndRoundTripsThroughTheCompressedFormat() {
        Document single = MusicXmlDocuments.singlePart(MusicXmlDocuments.read(bytes(TWO_PARTS)), "P2");
        byte[] compressed = MusicXmlDocuments.toCompressed(single);

        assertThat(MusicXmlDocuments.isZip(compressed)).isTrue();
        Document reread = MusicXmlDocuments.read(compressed);
        assertThat(MusicXmlDocuments.parts(reread)).containsExactly(new PartInfo("P2", "Clarinetto", 1));
        assertThat(reread.getElementsByTagName("score-part").getLength()).isEqualTo(1);
    }

    @Test
    void readsCompressedScoresFromTheirContainer() throws IOException {
        byte[] archive = zip(
            "META-INF/container.xml",
            "<container><rootfiles><rootfile full-path=\"scores/tuba.musicxml\" media-type=\"application/vnd.recordare.musicxml+xml\"/></rootfiles></container>",
            "scores/tuba.musicxml",
            TWO_PARTS
        );

        assertThat(MusicXmlDocuments.parts(MusicXmlDocuments.read(archive))).hasSize(2);
    }

    @Test
    void rejectsExternalEntities() {
        String attack = """
            <?xml version="1.0"?>
            <!DOCTYPE score-partwise [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
            <score-partwise><part-list><score-part id="P1"><part-name>&secret;</part-name></score-part></part-list><part id="P1"/></score-partwise>
            """;

        try {
            Document document = MusicXmlDocuments.read(bytes(attack));
            assertThat(MusicXmlDocuments.parts(document)).allSatisfy(part -> assertThat(part.name()).doesNotContain("root"));
        } catch (MusicXmlException exception) {
            assertThat(exception.reason()).isEqualTo("malformed");
        }
    }

    @Test
    void rejectsFilesThatAreNotPartwiseScores() {
        assertThatThrownBy(() -> MusicXmlDocuments.read(bytes("<score-timewise/>")))
            .isInstanceOfSatisfying(MusicXmlException.class, exception -> assertThat(exception.reason()).isEqualTo("timewise"));
        assertThatThrownBy(() -> MusicXmlDocuments.read(bytes("<html/>")))
            .isInstanceOfSatisfying(MusicXmlException.class, exception -> assertThat(exception.reason()).isEqualTo("notMusicXml"));
        assertThatThrownBy(() -> MusicXmlDocuments.read(bytes("not xml")))
            .isInstanceOfSatisfying(MusicXmlException.class, exception -> assertThat(exception.reason()).isEqualTo("malformed"));
        assertThatThrownBy(() -> MusicXmlDocuments.read(new byte[0]))
            .isInstanceOfSatisfying(MusicXmlException.class, exception -> assertThat(exception.reason()).isEqualTo("empty"));
    }

    @Test
    void rejectsArchivesWithTooManyEntries() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (int index = 0; index <= MusicXmlDocuments.MAX_ZIP_ENTRIES; index++) {
                zip.putNextEntry(new ZipEntry("file-" + index + ".txt"));
                zip.write('x');
                zip.closeEntry();
            }
        }

        assertThatThrownBy(() -> MusicXmlDocuments.read(output.toByteArray()))
            .isInstanceOfSatisfying(MusicXmlException.class, exception -> assertThat(exception.reason()).isEqualTo("archiveTooLarge"));
    }

    @Test
    void checksDurationsStructureAndTempo() {
        ScoreChecks tuba = MusicXmlInspector.inspect(MusicXmlDocuments.singlePart(MusicXmlDocuments.read(bytes(TWO_PARTS)), "P1"));

        assertThat(tuba.measureCount()).isEqualTo(3);
        assertThat(tuba.wrongDurationCount()).isEqualTo(1);
        assertThat(tuba.wrongDurationMeasures()).containsExactly("3");
        assertThat(tuba.forwardRepeats()).isEqualTo(1);
        assertThat(tuba.backwardRepeats()).isEqualTo(1);
        assertThat(tuba.openRepeats()).isZero();
        assertThat(tuba.tempo()).isTrue();
        assertThat(tuba.correctDurationPercent()).isEqualTo(67);

        ScoreChecks clarinet = MusicXmlInspector.inspect(MusicXmlDocuments.singlePart(MusicXmlDocuments.read(bytes(TWO_PARTS)), "P2"));
        assertThat(clarinet.wrongDurationCount()).isZero();
        assertThat(clarinet.chordNotes()).isEqualTo(1);
        assertThat(clarinet.notes()).isEqualTo(1);
        assertThat(clarinet.tempo()).isFalse();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] zip(String... namesAndContents) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (int index = 0; index < namesAndContents.length; index += 2) {
                zip.putNextEntry(new ZipEntry(namesAndContents[index]));
                zip.write(bytes(namesAndContents[index + 1]));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
