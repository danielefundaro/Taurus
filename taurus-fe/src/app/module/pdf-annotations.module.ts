import { ImageCrop } from './track-page-image.module';

export interface PdfCropRegion extends ImageCrop {
    page: number;
}

export interface PdfPageTransform {
    page: number;
    recipeVersion: 1;
    rotationQuarterTurns: number;
    deskewDegrees: number;
    crops: ImageCrop[];
    grayscale: boolean;
    brightness: number;
    contrast: number;
    autoContrast: boolean;
    threshold: number | null;
}

export interface PdfAnnotations {
    excludedPages: number[];
    /** Legacy crop-only representation, accepted for queued uploads created by older clients. */
    cropRegions: PdfCropRegion[];
    pageTransforms: PdfPageTransform[];
}
