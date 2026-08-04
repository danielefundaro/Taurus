package com.fundaro.zodiac.taurus.utils.pdf;

import java.util.List;

public class PdfAnnotations {

    private List<Integer> excludedPages;
    private List<PdfCropRegion> cropRegions;

    public List<Integer> getExcludedPages() {
        return excludedPages;
    }

    public void setExcludedPages(List<Integer> excludedPages) {
        this.excludedPages = excludedPages;
    }

    public List<PdfCropRegion> getCropRegions() {
        return cropRegions;
    }

    public void setCropRegions(List<PdfCropRegion> cropRegions) {
        this.cropRegions = cropRegions;
    }
}
