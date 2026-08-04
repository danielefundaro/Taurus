export interface PdfCropRegion {
    page: number;
    x: number;      // normalized [0–1], left edge from page left
    y: number;      // normalized [0–1], top edge from page top
    width: number;  // normalized [0–1]
    height: number; // normalized [0–1]
}

export interface PdfAnnotations {
    excludedPages: number[];      // 1-based page numbers to skip entirely
    cropRegions: PdfCropRegion[]; // per-page crop regions to apply
}
