package com.fundaro.zodiac.taurus.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class TrackPageImageDTOs {

    private TrackPageImageDTOs() {
    }

    public record Crop(
        @DecimalMin("0.0") @DecimalMax("1.0") double x,
        @DecimalMin("0.0") @DecimalMax("1.0") double y,
        @DecimalMin("0.02") @DecimalMax("1.0") double width,
        @DecimalMin("0.02") @DecimalMax("1.0") double height
    ) {
    }

    public record EditRequest(
        @NotNull Long expectedTrackVersion,
        @Min(1) @Max(1) int recipeVersion,
        @Min(-3) @Max(3) int rotationQuarterTurns,
        @DecimalMin("0.0") @DecimalMax("360.0") double deskewDegrees,
        @NotNull @Size(max = 8) List<@Valid Crop> crops,
        boolean grayscale,
        @Min(-100) @Max(100) int brightness,
        @Min(-100) @Max(100) int contrast,
        boolean autoContrast,
        @Min(0) @Max(255) Integer threshold
    ) {
    }

    public record Analysis(
        Long mediaId,
        int width,
        int height,
        Crop contentBounds,
        double blankBorderRatio,
        double estimatedSkewDegrees,
        double brightnessScore,
        double contrastScore,
        List<String> suggestions,
        List<String> warnings
    ) {
    }

    public record EditResult(
        Long trackId,
        Long trackVersion,
        Long scoreId,
        Long replacedMediaId,
        List<ChildrenEntitiesDTO> media
    ) {
    }
}
