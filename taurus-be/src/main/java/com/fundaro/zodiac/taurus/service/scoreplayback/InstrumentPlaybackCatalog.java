package com.fundaro.zodiac.taurus.service.scoreplayback;

import java.util.List;

/**
 * Reference catalogue used to propose the playback settings of an instrument from its name.
 * <p>
 * Every entry lists the keywords that identify the instrument, the key it is usually written in (pitch class, C = 0) and
 * its default values. Values follow the track score playback specification and are meant to be confirmed by an archivist.
 */
public final class InstrumentPlaybackCatalog {

    /** Sound bank of the General MIDI sounds. */
    public static final int GENERAL_MIDI_BANK = 0;

    private static final int C = 0;
    private static final int E_FLAT = 3;
    private static final int F = 5;
    private static final int B_FLAT = 10;

    private static final List<Entry> ENTRIES = List.of(
        pitched(List.of("ottavino"), C, 12, null, 72),
        pitched(List.of("flauto"), C, 0, null, 73),
        pitched(List.of("oboe"), C, 0, null, 68),
        pitched(List.of("corno inglese"), F, -7, null, 69),
        pitched(List.of("fagotto"), C, 0, 0, 70),
        pitched(List.of("clarinetto piccolo"), E_FLAT, 3, null, 71),
        pitched(List.of("clarinetto contralto"), E_FLAT, -9, null, 71),
        pitched(List.of("clarinetto basso"), B_FLAT, -14, -2, 71),
        pitched(List.of("clarinetto"), B_FLAT, -2, null, 71),
        pitched(List.of("sax soprano"), B_FLAT, -2, null, 64),
        pitched(List.of("sax contralto", "sax alto"), E_FLAT, -9, null, 65),
        pitched(List.of("sax tenore"), B_FLAT, -14, null, 66),
        pitched(List.of("sax baritono"), E_FLAT, -21, null, 67),
        pitched(List.of("tromba", "cornetta"), B_FLAT, -2, null, 56),
        pitched(List.of("flicorno sopranino"), E_FLAT, 3, null, 56),
        pitched(List.of("flicorno soprano"), B_FLAT, -2, null, 56),
        pitched(List.of("flicorno contralto"), E_FLAT, -9, null, 60),
        pitched(List.of("flicorno tenore"), B_FLAT, -14, 0, 57),
        pitched(List.of("flicorno baritono", "euphonium", "eufonio", "bombardino"), B_FLAT, -14, 0, 57),
        pitched(List.of("flicorno basso"), B_FLAT, -14, 0, 58),
        pitched(List.of("trombone"), C, 0, 0, 57),
        pitched(List.of("corno"), F, -7, null, 60),
        pitched(List.of("basso", "tuba", "bassotuba"), C, 0, 0, 58),
        pitched(List.of("glockenspiel", "campanelli"), C, 24, null, 9),
        pitched(List.of("vibrafono"), C, 0, null, 11),
        pitched(List.of("xilofono"), C, 12, null, 13),
        pitched(List.of("timpani", "timpano"), C, 0, 0, 47),
        percussion(List.of("tamburo", "rullante"), 38),
        percussion(List.of("gran cassa", "grancassa"), 36),
        percussion(List.of("piatti", "piatto"), 49),
        percussion(List.of("triangolo"), 81),
        percussion(List.of("tamburello"), 54)
    );

    private InstrumentPlaybackCatalog() {}

    public static List<Entry> entries() {
        return ENTRIES;
    }

    private static Entry pitched(List<String> keywords, int referenceKey, int transposition, Integer bassClefTransposition, int soundProgram) {
        return new Entry(keywords, referenceKey, transposition, bassClefTransposition, soundProgram, null);
    }

    private static Entry percussion(List<String> keywords, int percussionKey) {
        return new Entry(keywords, null, null, null, null, percussionKey);
    }

    /**
     * A reference instrument.
     *
     * @param keywords              normalized keywords, matched as whole words
     * @param referenceKey          pitch class of the usual key (C = 0), {@code null} for unpitched percussion
     * @param transposition         semitones from written to sounding pitch
     * @param bassClefTransposition semitones for parts written in bass clef, {@code null} when equal to {@code transposition}
     * @param soundProgram          General MIDI program, {@code null} for unpitched percussion
     * @param percussionKey         General MIDI drum key, {@code null} for pitched instruments
     */
    public record Entry(
        List<String> keywords,
        Integer referenceKey,
        Integer transposition,
        Integer bassClefTransposition,
        Integer soundProgram,
        Integer percussionKey
    ) {}
}
