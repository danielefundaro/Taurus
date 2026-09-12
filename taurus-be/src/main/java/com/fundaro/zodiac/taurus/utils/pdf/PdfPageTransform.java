package com.fundaro.zodiac.taurus.utils.pdf;

import java.util.List;

public class PdfPageTransform {

    private int page;
    private int recipeVersion = 1;
    private int rotationQuarterTurns;
    private double deskewDegrees;
    private List<PageEditRecipe.Crop> crops = List.of();
    private boolean grayscale;
    private int brightness;
    private int contrast;
    private boolean autoContrast;
    private Integer threshold;

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getRecipeVersion() { return recipeVersion; }
    public void setRecipeVersion(int recipeVersion) { this.recipeVersion = recipeVersion; }
    public int getRotationQuarterTurns() { return rotationQuarterTurns; }
    public void setRotationQuarterTurns(int rotationQuarterTurns) { this.rotationQuarterTurns = rotationQuarterTurns; }
    public double getDeskewDegrees() { return deskewDegrees; }
    public void setDeskewDegrees(double deskewDegrees) { this.deskewDegrees = deskewDegrees; }
    public List<PageEditRecipe.Crop> getCrops() { return crops; }
    public void setCrops(List<PageEditRecipe.Crop> crops) { this.crops = crops == null ? List.of() : crops; }
    public boolean isGrayscale() { return grayscale; }
    public void setGrayscale(boolean grayscale) { this.grayscale = grayscale; }
    public int getBrightness() { return brightness; }
    public void setBrightness(int brightness) { this.brightness = brightness; }
    public int getContrast() { return contrast; }
    public void setContrast(int contrast) { this.contrast = contrast; }
    public boolean isAutoContrast() { return autoContrast; }
    public void setAutoContrast(boolean autoContrast) { this.autoContrast = autoContrast; }
    public Integer getThreshold() { return threshold; }
    public void setThreshold(Integer threshold) { this.threshold = threshold; }

    public PageEditRecipe toRecipe() {
        return new PageEditRecipe(
            recipeVersion,
            rotationQuarterTurns,
            deskewDegrees,
            crops,
            grayscale,
            brightness,
            contrast,
            autoContrast,
            threshold
        );
    }
}
