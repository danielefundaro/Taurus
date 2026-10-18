package com.fundaro.zodiac.taurus.service.scoreplayback;

import com.fundaro.zodiac.taurus.service.scoreplayback.MusicXmlInspector.ScoreChecks;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Choice of the version proposed after a recognition, among the engines that succeeded:
 * <ol>
 *     <li>the highest share of measures whose duration matches the time signature;</li>
 *     <li>within two percentage points, fewer suspicious clefs (changes, octave clefs, percussion clef in a pitched part);</li>
 *     <li>then Audiveris, which keeps dynamics and ties.</li>
 * </ol>
 */
public final class ScoreRecognitionProposal {

    static final String AUDIVERIS = "AUDIVERIS";
    private static final double DURATION_TOLERANCE = 2.0;

    private ScoreRecognitionProposal() {}

    /** A version read by an engine. */
    public record Candidate(String engine, ScoreChecks checks) {}

    /**
     * @param percussionPart the part belongs to percussion instruments: a percussion clef is expected
     */
    public static Optional<Candidate> choose(List<Candidate> candidates, boolean percussionPart) {
        if (candidates.isEmpty()) return Optional.empty();
        double best = candidates.stream().mapToDouble(candidate -> candidate.checks().correctDurationPercent()).max().orElse(0);
        return candidates.stream()
            .filter(candidate -> best - candidate.checks().correctDurationPercent() <= DURATION_TOLERANCE)
            .min(
                Comparator.comparingInt((Candidate candidate) -> suspiciousClefs(candidate.checks(), percussionPart))
                    .thenComparing(candidate -> !AUDIVERIS.equals(candidate.engine()))
                    .thenComparing(candidate -> -candidate.checks().correctDurationPercent())
            );
    }

    static int suspiciousClefs(ScoreChecks checks, boolean percussionPart) {
        return checks.clefChanges() + checks.octaveClefs() + (checks.percussionClef() && !percussionPart ? 1 : 0);
    }
}
