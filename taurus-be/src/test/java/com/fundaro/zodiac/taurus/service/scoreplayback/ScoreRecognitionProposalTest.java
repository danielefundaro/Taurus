package com.fundaro.zodiac.taurus.service.scoreplayback;

import com.fundaro.zodiac.taurus.service.scoreplayback.MusicXmlInspector.ScoreChecks;
import com.fundaro.zodiac.taurus.service.scoreplayback.ScoreRecognitionProposal.Candidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreRecognitionProposalTest {

    @Test
    void prefersTheVersionWithMoreMeasuresOfTheRightLength() {
        Candidate audiveris = new Candidate("AUDIVERIS", checks(100, 10, 0, 0, false));
        Candidate homr = new Candidate("HOMR", checks(100, 1, 3, 0, false));

        assertThat(ScoreRecognitionProposal.choose(List.of(audiveris, homr), false)).contains(homr);
    }

    @Test
    void withinTwoPointsPrefersFewerSuspiciousClefsThenAudiveris() {
        Candidate audiveris = new Candidate("AUDIVERIS", checks(100, 2, 2, 1, false));
        Candidate homr = new Candidate("HOMR", checks(100, 0, 0, 0, false));
        Candidate cleanAudiveris = new Candidate("AUDIVERIS", checks(100, 1, 0, 0, false));

        assertThat(ScoreRecognitionProposal.choose(List.of(audiveris, homr), false)).contains(homr);
        assertThat(ScoreRecognitionProposal.choose(List.of(homr, cleanAudiveris), false)).contains(cleanAudiveris);
    }

    @Test
    void aPercussionClefIsSuspiciousOnlyInPitchedParts() {
        Candidate audiveris = new Candidate("AUDIVERIS", checks(50, 0, 0, 0, true));
        Candidate homr = new Candidate("HOMR", checks(50, 0, 0, 0, false));

        assertThat(ScoreRecognitionProposal.choose(List.of(audiveris, homr), false)).contains(homr);
        assertThat(ScoreRecognitionProposal.choose(List.of(audiveris, homr), true)).contains(audiveris);
        assertThat(ScoreRecognitionProposal.choose(List.of(), false)).isEmpty();
    }

    private static ScoreChecks checks(int measures, int wrongDurations, int clefChanges, int octaveClefs, boolean percussionClef) {
        return new ScoreChecks(measures, wrongDurations, List.of(), clefChanges, octaveClefs, percussionClef, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, true, 0, 100, 0);
    }
}
