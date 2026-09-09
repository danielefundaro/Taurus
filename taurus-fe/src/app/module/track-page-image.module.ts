import { ChildrenEntities } from './children-entities.module';

export interface ImageCrop {
    x: number;
    y: number;
    width: number;
    height: number;
}

export interface ImageTransformRecipe {
    expectedTrackVersion: number;
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

export interface TrackPageAnalysis {
    mediaId: number;
    width: number;
    height: number;
    contentBounds: ImageCrop;
    blankBorderRatio: number;
    estimatedSkewDegrees: number;
    brightnessScore: number;
    contrastScore: number;
    suggestions: Array<'AUTO_CROP' | 'DESKEW' | 'AUTO_CONTRAST'>;
    warnings: string[];
}

export interface TrackPageEditResult {
    trackId: number;
    trackVersion: number;
    scoreId: number;
    replacedMediaId: number;
    media: ChildrenEntities[];
}
