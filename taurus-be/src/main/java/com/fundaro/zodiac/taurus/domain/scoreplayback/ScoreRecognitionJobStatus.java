package com.fundaro.zodiac.taurus.domain.scoreplayback;

public enum ScoreRecognitionJobStatus {
    QUEUED,
    RUNNING,
    DONE,
    FAILED;

    public boolean isActive() {
        return this == QUEUED || this == RUNNING;
    }
}
