import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, ElementRef, ViewChild } from '@angular/core';
import { NgxExtendedPdfViewerModule, NgxExtendedPdfViewerService, PagesLoadedEvent, PDFExportScaleFactor } from 'ngx-extended-pdf-viewer';
import { BadgeModule } from 'primeng/badge';
import { ButtonModule } from 'primeng/button';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { TooltipModule } from 'primeng/tooltip';
import { ImportsModule } from '../../imports';
import { ImageCrop, ImageTransformRecipe } from '../../module';
import { PdfAnnotations, PdfCropRegion, PdfPageTransform } from '../../module/pdf-annotations.module';
import { ConfirmService } from '../../service';

interface CropRect {
    x: number;
    y: number;
    width: number;
    height: number;
}

/** Una zona dell'anteprima con gli estremi usati dal contrasto automatico, come il crop del backend. */
interface ToneRect {
    left: number;
    top: number;
    right: number;
    bottom: number;
}

interface ToneBounds {
    min: number;
    max: number;
}

interface ToneZone extends ToneRect {
    bounds: ToneBounds;
}

type CropDragMode = 'draw' | 'move' | 'resize-nw' | 'resize-ne' | 'resize-sw' | 'resize-se' | 'resize-n' | 'resize-s' | 'resize-e' | 'resize-w';

/** Pixels used as hit-test radius around each corner handle. */
const HANDLE_HIT_PX = 12;

@Component({
    selector: 'app-pdf-manipulator-dialog',
    standalone: true,
    imports: [CommonModule, NgxExtendedPdfViewerModule, ButtonModule, TooltipModule, BadgeModule, ConfirmDialogModule, ImportsModule],
    providers: [NgxExtendedPdfViewerService],
    templateUrl: './pdf-manipulator-dialog.component.html',
    styleUrl: './pdf-manipulator-dialog.component.scss'
})
export class PdfManipulatorDialogComponent {
    @ViewChild('cropContainer') cropContainerRef?: ElementRef<HTMLElement>;
    @ViewChild('previewCanvas') previewCanvasRef?: ElementRef<HTMLCanvasElement>;

    protected pdfFile: File;
    protected pages: number[] = [];
    protected currentPage: number | undefined = 1;
    protected pdfReady = false;

    protected excludedPages = new Set<number>();
    protected cropRegions = new Map<number, PdfCropRegion[]>();

    protected cropMode = false;
    protected cropPageNum: number | null = null;
    protected cropImage: string | undefined;
    protected cropRect: CropRect | null = null;
    protected cropLoading = false;
    protected cropCursor = 'default';
    protected cropDrawing = false;
    protected pageCrops: PdfCropRegion[] = [];
    protected recipe: ImageTransformRecipe = this.emptyRecipe();
    protected thresholdEnabled = false;

    private pageRecipes = new Map<number, ImageTransformRecipe>();
    private bitmap?: ImageBitmap;
    protected editingCropIndex: number | null = null;

    private dragMode: CropDragMode = 'draw';
    private cropRectAtDragStart: CropRect | null = null;
    private dragStartPos: { x: number; y: number } | null = null;
    private isDragging = false;

    constructor(
        private readonly dialogRef: DynamicDialogRef,
        private readonly config: DynamicDialogConfig,
        private readonly pdfViewerService: NgxExtendedPdfViewerService,
        private readonly confirmService: ConfirmService,
        private readonly changeDetectorRef: ChangeDetectorRef
    ) {
        this.pdfFile = this.config.data.file;
        const annotations = this.config.data.annotations as PdfAnnotations | null | undefined;
        this.excludedPages = new Set(annotations?.excludedPages ?? []);
        const transformedPages = new Set<number>();
        for (const transform of annotations?.pageTransforms ?? []) {
            this.pageRecipes.set(transform.page, this.recipeFromTransform(transform));
            transformedPages.add(transform.page);
        }
        for (const crop of annotations?.cropRegions ?? []) {
            if (transformedPages.has(crop.page)) continue;
            const recipe = this.pageRecipes.get(crop.page) ?? this.emptyRecipe();
            recipe.crops.push({ x: crop.x, y: crop.y, width: crop.width, height: crop.height });
            this.pageRecipes.set(crop.page, recipe);
        }
        this.syncCropRegions();
    }

    protected onPagesLoaded(event: PagesLoadedEvent): void {
        const count = event.pagesCount;
        this.pages = Array.from({ length: count }, (_, i) => i + 1);
        this.pdfReady = true;
        // PDF.js calcola il viewport al caricamento, prima che il dialogo abbia le sue dimensioni finali.
        // Un evento resize forzato fa ricalcolare le page regions e dipinge il canvas.
        setTimeout(() => window.dispatchEvent(new Event('resize')), 50);
    }

    protected navigateTo(page: number): void {
        this.currentPage = page;
    }

    protected isExcluded(page: number): boolean {
        return this.excludedPages.has(page);
    }

    protected cropCountForPage(page: number): number {
        return this.cropRegions.get(page)?.length ?? 0;
    }

    protected hasTransform(page: number): boolean {
        const recipe = this.pageRecipes.get(page);
        return !!recipe && !this.isEmptyRecipe(recipe);
    }

    protected toggleExclude(page: number, event: Event): void {
        event.stopPropagation();
        if (this.excludedPages.has(page)) {
            this.excludedPages.delete(page);
        } else {
            this.excludedPages.add(page);
        }
        this.excludedPages = new Set(this.excludedPages);
    }

    protected get allExcluded(): boolean {
        return this.pages.length > 0 && this.excludedPages.size === this.pages.length;
    }

    protected toggleExcludeAll(event: Event): void {
        event.stopPropagation();
        this.excludedPages = this.allExcluded ? new Set() : new Set(this.pages);
    }

    protected async enterCropMode(pageNum: number, event: Event): Promise<void> {
        event.stopPropagation();
        if (!this.pdfReady || this.cropLoading) return;

        this.cropLoading = true;
        this.currentPage = pageNum;
        await new Promise((resolve) => setTimeout(resolve, 150));

        const scale: PDFExportScaleFactor = { scale: 1.5 };
        const image = await this.pdfViewerService.getPageAsImage(pageNum, scale);
        this.cropLoading = false;

        if (!image) return;

        this.cropImage = image;
        this.bitmap?.close();
        this.bitmap = await createImageBitmap(await (await fetch(image)).blob());
        this.cropPageNum = pageNum;
        this.recipe = this.cloneRecipe(this.pageRecipes.get(pageNum) ?? this.emptyRecipe());
        this.thresholdEnabled = this.recipe.threshold !== null;
        this.pageCrops = this.recipe.crops.map((r) => ({ page: pageNum, ...r }));
        this.cropRect = null;
        this.cropDrawing = false;
        this.editingCropIndex = null;
        this.cropCursor = 'default';
        this.activateCropEditor();
    }

    protected exitCropMode(): void {
        this.cropMode = false;
        this.cropPageNum = null;
        this.cropImage = undefined;
        this.cropRect = null;
        this.pageCrops = [];
        this.cropDrawing = false;
        this.editingCropIndex = null;
        this.dragStartPos = null;
        this.cropRectAtDragStart = null;
        this.isDragging = false;
        this.cropCursor = 'default';
        this.bitmap?.close();
        this.bitmap = undefined;
    }

    protected applyCrop(): void {
        if (!this.cropRect || this.cropPageNum === null) return;
        const isSignificant = this.cropRect.width >= 0.02 && this.cropRect.height >= 0.02;
        if (!isSignificant) return;
        const newCrop: PdfCropRegion = { page: this.cropPageNum, ...this.cropRect };
        let activeIndex = this.editingCropIndex;
        if (activeIndex !== null) {
            this.pageCrops = this.pageCrops.map((crop, index) => (index === activeIndex ? newCrop : crop));
        } else {
            if (this.pageCrops.length >= 8) return;
            this.pageCrops = [...this.pageCrops, newCrop];
            activeIndex = this.pageCrops.length - 1;
        }
        this.syncCurrentPageCrops();
        this.cropRect = { ...newCrop };
        this.cropDrawing = false;
        this.editingCropIndex = activeIndex;
        this.cropCursor = 'default';
    }

    protected editPageCrop(index: number): void {
        const crop = this.pageCrops[index];
        if (!crop) return;
        this.cropRect = { x: crop.x, y: crop.y, width: crop.width, height: crop.height };
        this.cropDrawing = false;
        this.editingCropIndex = index;
        this.cropCursor = 'default';
    }

    protected startAddingCrop(): void {
        if (this.pageCrops.length >= 8) return;
        this.cropRect = null;
        this.cropDrawing = true;
        this.editingCropIndex = null;
        this.cropCursor = 'crosshair';
    }

    protected splitCrop(direction: 'vertical' | 'horizontal'): void {
        if (this.cropPageNum === null) return;
        this.pageCrops =
            direction === 'vertical'
                ? [
                    { page: this.cropPageNum, x: 0, y: 0, width: 0.5, height: 1 },
                    { page: this.cropPageNum, x: 0.5, y: 0, width: 0.5, height: 1 }
                ]
                : [
                    { page: this.cropPageNum, x: 0, y: 0, width: 1, height: 0.5 },
                    { page: this.cropPageNum, x: 0, y: 0.5, width: 1, height: 0.5 }
                ];
        this.cropRect = { ...this.pageCrops[0] };
        this.cropDrawing = false;
        this.editingCropIndex = 0;
        this.cropCursor = 'default';
        this.syncCurrentPageCrops();
    }

    protected updateCrop(field: keyof CropRect, value: number | null): void {
        if (!this.cropRect) return;
        const crop = { ...this.cropRect, [field]: Number(value ?? 0) };
        crop.x = this.clamp(crop.x, 0, 0.98);
        crop.y = this.clamp(crop.y, 0, 0.98);
        crop.width = this.clamp(crop.width, 0.02, 1 - crop.x);
        crop.height = this.clamp(crop.height, 0.02, 1 - crop.y);
        this.cropRect = crop;
        if (this.editingCropIndex !== null && this.cropPageNum !== null) {
            const updated: PdfCropRegion = { page: this.cropPageNum, ...crop };
            this.pageCrops = this.pageCrops.map((current, index) => (index === this.editingCropIndex ? updated : current));
            this.syncCurrentPageCrops();
        }
    }

    protected removeCrop(): void {
        if (this.cropPageNum !== null) {
            this.pageCrops = [];
            this.cropRegions.delete(this.cropPageNum);
            this.cropRegions = new Map(this.cropRegions);
            this.commitCurrentRecipe();
            this.cropDrawing = false;
            this.editingCropIndex = null;
            this.cropRect = null;
            this.cropCursor = 'default';
        }
    }

    protected removePageCrop(index: number, event?: Event): void {
        event?.stopPropagation();
        this.pageCrops = this.pageCrops.filter((_, i) => i !== index);
        if (this.pageCrops.length) this.editPageCrop(Math.min(index, this.pageCrops.length - 1));
        else {
            this.cropRect = null;
            this.cropDrawing = false;
            this.editingCropIndex = null;
            this.cropCursor = 'default';
        }
        if (this.cropPageNum !== null) {
            this.syncCurrentPageCrops();
        }
    }

    protected removeCropFromAll(): void {
        this.cropRegions = new Map();
        const recipes = new Map<number, ImageTransformRecipe>();
        for (const [page, recipe] of this.pageRecipes) {
            const withoutCrops: ImageTransformRecipe = { ...this.cloneRecipe(recipe), crops: [] };
            if (!this.isEmptyRecipe(withoutCrops)) recipes.set(page, withoutCrops);
        }
        this.pageRecipes = recipes;
        if (this.cropPageNum !== null) {
            this.recipe.crops = [];
            this.pageCrops = [];
            this.cropRect = null;
            this.cropDrawing = false;
            this.editingCropIndex = null;
            this.cropCursor = 'default';
        }
    }

    protected applyCropToAll(): void {
        if (this.cropPageNum === null) return;
        const cropsToApply = this.effectivePageCrops();
        if (cropsToApply.length === 0 && this.isEmptyRecipe(this.recipe)) return;
        const newMap = new Map<number, PdfCropRegion[]>();
        const sourceRecipe = this.cloneRecipe(this.recipe);
        sourceRecipe.crops = cropsToApply.map(({ x, y, width, height }) => ({ x, y, width, height }));
        const recipes = new Map<number, ImageTransformRecipe>();
        for (const page of this.pages) {
            newMap.set(
                page,
                cropsToApply.map((c) => ({ ...c, page }))
            );
            recipes.set(page, this.cloneRecipe(sourceRecipe));
        }
        this.cropRegions = newMap;
        this.pageRecipes = recipes;
        this.exitCropMode();
    }

    protected rotate(direction: -1 | 1): void {
        this.recipe.rotationQuarterTurns = (((this.recipe.rotationQuarterTurns + direction) % 4) + 4) % 4;
        this.settingsChanged();
    }

    protected toggleThreshold(enabled: boolean): void {
        this.thresholdEnabled = enabled;
        this.recipe.threshold = enabled ? 180 : null;
        this.settingsChanged();
    }

    protected settingsChanged(): void {
        this.commitCurrentRecipe();
        this.renderPreview();
    }

    protected resetCurrentPage(): void {
        if (this.cropPageNum === null) return;
        this.recipe = this.emptyRecipe();
        this.thresholdEnabled = false;
        this.pageCrops = [];
        this.pageRecipes.delete(this.cropPageNum);
        this.cropRegions.delete(this.cropPageNum);
        this.cropRegions = new Map(this.cropRegions);
        this.cropRect = null;
        this.cropDrawing = false;
        this.editingCropIndex = null;
        this.cropCursor = 'default';
        this.renderPreview();
    }

    protected sliderPosition(value: number | null, minimum: number, maximum: number): number {
        const current = value ?? minimum;
        return ((this.clamp(current, minimum, maximum) - minimum) / (maximum - minimum)) * 100;
    }

    // ── Pointer handlers ────────────────────────────────────────────────────────

    protected onCropPointerDown(event: PointerEvent): void {
        const { img, rect } = this.getImgAndRect();
        if (!img || !rect) return;

        const [mx, my] = this.normalize(event, rect);

        if (this.cropDrawing) {
            if (this.pageCrops.length >= 8) return;
            this.startDrag('draw', mx, my);
            this.cropRect = null;
            this.editingCropIndex = null;
            this.capturePointer(event);
            event.preventDefault();
            return;
        }

        if (this.cropRect) {
            const hx = HANDLE_HIT_PX / rect.width;
            const hy = HANDLE_HIT_PX / rect.height;
            const { x, y, width, height } = this.cropRect;

            const corners: Array<[CropDragMode, number, number]> = [
                ['resize-nw', x, y],
                ['resize-ne', x + width, y],
                ['resize-sw', x, y + height],
                ['resize-se', x + width, y + height]
            ];

            for (const [mode, cx, cy] of corners) {
                if (Math.abs(mx - cx) < hx && Math.abs(my - cy) < hy) {
                    this.startDrag(mode, mx, my);
                    this.capturePointer(event);
                    event.preventDefault();
                    return;
                }
            }

            // Edge hit-tests (checked after corners to avoid overlap)
            const edges: Array<[CropDragMode, boolean]> = [
                ['resize-n', Math.abs(my - y) < hy && mx > x + hx && mx < x + width - hx],
                ['resize-s', Math.abs(my - (y + height)) < hy && mx > x + hx && mx < x + width - hx],
                ['resize-w', Math.abs(mx - x) < hx && my > y + hy && my < y + height - hy],
                ['resize-e', Math.abs(mx - (x + width)) < hx && my > y + hy && my < y + height - hy]
            ];

            for (const [mode, hit] of edges) {
                if (hit) {
                    this.startDrag(mode, mx, my);
                    this.capturePointer(event);
                    event.preventDefault();
                    return;
                }
            }

            if (mx >= x && mx <= x + width && my >= y && my <= y + height) {
                this.startDrag('move', mx, my);
                this.capturePointer(event);
                event.preventDefault();
                return;
            }
        }

        const cropIndex = this.cropAt(mx, my);
        if (cropIndex >= 0) {
            this.editPageCrop(cropIndex);
            event.preventDefault();
        }
    }

    protected onCropPointerMove(event: PointerEvent): void {
        const { img, rect } = this.getImgAndRect();
        if (!img || !rect) return;

        const [mx, my] = this.normalize(event, rect);

        if (!this.isDragging) {
            this.cropCursor = this.cropDrawing ? 'crosshair' : this.computeCursor(mx, my, rect);
            return;
        }

        switch (this.dragMode) {
            case 'draw':
                if (this.dragStartPos) {
                    this.cropRect = {
                        x: Math.min(this.dragStartPos.x, mx),
                        y: Math.min(this.dragStartPos.y, my),
                        width: Math.abs(mx - this.dragStartPos.x),
                        height: Math.abs(my - this.dragStartPos.y)
                    };
                }
                break;

            case 'move':
                if (this.cropRectAtDragStart && this.dragStartPos) {
                    const dx = mx - this.dragStartPos.x;
                    const dy = my - this.dragStartPos.y;
                    this.cropRect = {
                        ...this.cropRectAtDragStart,
                        x: Math.min(Math.max(this.cropRectAtDragStart.x + dx, 0), 1 - this.cropRectAtDragStart.width),
                        y: Math.min(Math.max(this.cropRectAtDragStart.y + dy, 0), 1 - this.cropRectAtDragStart.height)
                    };
                }
                break;

            default:
                if (this.cropRectAtDragStart) {
                    this.cropRect = this.computeResizedRect(this.dragMode, this.cropRectAtDragStart, mx, my);
                }
        }
    }

    protected onCropPointerUp(event: PointerEvent): void {
        if (!this.isDragging) {
            this.releasePointer(event);
            return;
        }
        this.onCropPointerMove(event);
        const completedMode = this.dragMode;
        this.isDragging = false;
        this.cropRectAtDragStart = null;
        this.dragStartPos = null;
        this.releasePointer(event);
        if (!this.cropRect) return;

        if (completedMode === 'draw') {
            if (this.cropRect.width >= 0.02 && this.cropRect.height >= 0.02) this.applyCrop();
            else this.cropRect = null;
            return;
        }

        this.applyCrop();
    }

    protected onCropPointerCancel(event: PointerEvent): void {
        if (this.dragMode === 'draw') this.cropRect = null;
        else if (this.cropRectAtDragStart) this.cropRect = { ...this.cropRectAtDragStart };
        this.isDragging = false;
        this.cropRectAtDragStart = null;
        this.dragStartPos = null;
        this.cropCursor = this.cropDrawing ? 'crosshair' : 'default';
        this.releasePointer(event);
    }

    // ── Styles ──────────────────────────────────────────────────────────────────

    protected get cropSelectionStyle(): Record<string, string> {
        if (!this.cropRect) return {};
        return {
            left: `${this.cropRect.x * 100}%`,
            top: `${this.cropRect.y * 100}%`,
            width: `${this.cropRect.width * 100}%`,
            height: `${this.cropRect.height * 100}%`
        };
    }

    protected existingCropStyle(crop: PdfCropRegion): Record<string, string> {
        return {
            left: `${crop.x * 100}%`,
            top: `${crop.y * 100}%`,
            width: `${crop.width * 100}%`,
            height: `${crop.height * 100}%`
        };
    }

    // ── Counters ─────────────────────────────────────────────────────────────────

    protected get excludedCount(): number {
        return this.excludedPages.size;
    }

    protected get cropCount(): number {
        let total = 0;
        for (const crops of this.cropRegions.values()) total += crops.length;
        return total;
    }

    protected get editedPageCount(): number {
        return Array.from(this.pageRecipes.values()).filter((recipe) => !this.isEmptyRecipe(recipe)).length;
    }

    // ── Dialog actions ───────────────────────────────────────────────────────────

    protected confirm(): void {
        if (this.allExcluded) return;
        const annotations: PdfAnnotations = {
            excludedPages: Array.from(this.excludedPages),
            cropRegions: Array.from(this.cropRegions.values()).flat(),
            pageTransforms: Array.from(this.pageRecipes, ([page, recipe]) => this.transformFromRecipe(page, recipe)).filter((transform) => !this.isEmptyRecipe(this.recipeFromTransform(transform)))
        };
        this.dialogRef.close(annotations);
    }

    protected cancel(): void {
        const hasChanges = this.excludedPages.size > 0 || this.pageRecipes.size > 0;
        if (!hasChanges) {
            this.dialogRef.close(null);
            return;
        }
        this.confirmService.confirmDiscard({
            title: 'Uscire senza salvare?',
            consequence: 'Le modifiche alle pagine e ai ritagli andranno perse.',
            actionLabel: 'Esci senza salvare',
            key: 'pdf-discard',
            accept: () => this.dialogRef.close(null)
        });
    }

    // ── Private helpers ──────────────────────────────────────────────────────────

    private activateCropEditor(): void {
        this.cropMode = true;
        // The canvas is inside an @if block. Materialize it before the first draw;
        // otherwise the initial render is lost and a second click is required.
        this.changeDetectorRef.detectChanges();
        this.renderPreview();
    }

    private effectivePageCrops(): PdfCropRegion[] {
        const crops = this.pageCrops.map((crop) => ({ ...crop }));
        if (!this.cropRect || this.cropPageNum === null || this.cropRect.width < 0.02 || this.cropRect.height < 0.02) return crops;
        const pending = { page: this.cropPageNum, ...this.cropRect };
        if (this.editingCropIndex !== null) crops[this.editingCropIndex] = pending;
        else if (crops.length < 8) crops.push(pending);
        return crops;
    }

    private syncCurrentPageCrops(): void {
        if (this.cropPageNum === null) return;
        if (this.pageCrops.length)
            this.cropRegions.set(
                this.cropPageNum,
                this.pageCrops.map((crop) => ({ ...crop, page: this.cropPageNum! }))
            );
        else this.cropRegions.delete(this.cropPageNum);
        this.cropRegions = new Map(this.cropRegions);
        this.commitCurrentRecipe();
    }

    private commitCurrentRecipe(): void {
        if (this.cropPageNum === null) return;
        this.recipe.crops = this.pageCrops.map(({ x, y, width, height }) => ({ x, y, width, height }));
        if (this.isEmptyRecipe(this.recipe)) this.pageRecipes.delete(this.cropPageNum);
        else this.pageRecipes.set(this.cropPageNum, this.cloneRecipe(this.recipe));
    }

    private syncCropRegions(): void {
        const regions = new Map<number, PdfCropRegion[]>();
        for (const [page, recipe] of this.pageRecipes) {
            if (recipe.crops.length)
                regions.set(
                    page,
                    recipe.crops.map((crop) => ({ page, ...crop }))
                );
        }
        this.cropRegions = regions;
    }

    private emptyRecipe(): ImageTransformRecipe {
        return {
            expectedTrackVersion: 0,
            recipeVersion: 1,
            rotationQuarterTurns: 0,
            deskewDegrees: 0,
            crops: [],
            grayscale: false,
            brightness: 0,
            contrast: 0,
            autoContrast: false,
            threshold: null
        };
    }

    private cloneRecipe(recipe: ImageTransformRecipe): ImageTransformRecipe {
        return { ...recipe, crops: recipe.crops.map((crop) => ({ ...crop })) };
    }

    private recipeFromTransform(transform: PdfPageTransform): ImageTransformRecipe {
        return { expectedTrackVersion: 0, ...transform, deskewDegrees: this.normalizeAngle(transform.deskewDegrees), crops: transform.crops.map((crop) => ({ ...crop })) };
    }

    private transformFromRecipe(page: number, recipe: ImageTransformRecipe): PdfPageTransform {
        return {
            page,
            recipeVersion: 1,
            rotationQuarterTurns: recipe.rotationQuarterTurns,
            deskewDegrees: recipe.deskewDegrees,
            crops: recipe.crops.map((crop) => ({ ...crop })),
            grayscale: recipe.grayscale,
            brightness: recipe.brightness,
            contrast: recipe.contrast,
            autoContrast: recipe.autoContrast,
            threshold: recipe.threshold
        };
    }

    private isEmptyRecipe(recipe: ImageTransformRecipe): boolean {
        return recipe.rotationQuarterTurns === 0 && this.isZeroAngle(recipe.deskewDegrees) && recipe.crops.length === 0 && !recipe.grayscale && recipe.brightness === 0 && recipe.contrast === 0 && !recipe.autoContrast && recipe.threshold === null;
    }

    private renderPreview(): void {
        const canvas = this.previewCanvasRef?.nativeElement;
        if (!canvas || !this.bitmap) return;
        const turns = ((this.recipe.rotationQuarterTurns % 4) + 4) % 4;
        const rotatedWidth = turns % 2 ? this.bitmap.height : this.bitmap.width;
        const rotatedHeight = turns % 2 ? this.bitmap.width : this.bitmap.height;
        const scale = Math.min(1, 1400 / Math.max(rotatedWidth, rotatedHeight));
        canvas.width = Math.max(1, Math.round(rotatedWidth * scale));
        canvas.height = Math.max(1, Math.round(rotatedHeight * scale));
        const context = canvas.getContext('2d', { willReadFrequently: true })!;
        context.fillStyle = '#fff';
        context.fillRect(0, 0, canvas.width, canvas.height);
        context.save();
        context.translate(canvas.width / 2, canvas.height / 2);
        context.rotate((turns * Math.PI) / 2 + (this.normalizeAngle(this.recipe.deskewDegrees) * Math.PI) / 180);
        context.drawImage(this.bitmap, (-this.bitmap.width * scale) / 2, (-this.bitmap.height * scale) / 2, this.bitmap.width * scale, this.bitmap.height * scale);
        context.restore();
        this.applyTone(context, canvas.width, canvas.height);
    }

    /**
     * Il backend calcola i toni dopo il crop, una zona per volta: l'anteprima fa lo stesso, così il
     * contrasto automatico di ogni zona usa i propri estremi. I pixel fuori dalle zone sono scartati
     * dal backend e restano qui come contesto, con gli estremi dell'intera pagina.
     */
    private applyTone(context: CanvasRenderingContext2D, width: number, height: number): void {
        const image = context.getImageData(0, 0, width, height);
        const values = image.data;
        const zones = this.toneZones(values, width, height);
        const page = zones.length === 1 ? zones[0].bounds : this.toneBounds(values, { left: 0, top: 0, right: width, bottom: height }, width);
        const factor = 1 + this.recipe.contrast / 100;
        const offset = Math.round(this.recipe.brightness * 2.55);

        for (let y = 0; y < height; y++) {
            for (let x = 0; x < width; x++) {
                const zone = zones.find((candidate) => x >= candidate.left && x < candidate.right && y >= candidate.top && y < candidate.bottom);
                this.applyPixel(values, (y * width + x) * 4, zone ? zone.bounds : page, factor, offset);
            }
        }
        context.putImageData(image, 0, 0);
    }

    /** Le zone del backend: i crop della ricetta, oppure l'intera pagina quando non ce ne sono. */
    private toneZones(values: Uint8ClampedArray, width: number, height: number): ToneZone[] {
        const rectangles: ToneRect[] = this.recipe.crops.length
            ? this.recipe.crops.map((crop) => this.toneRect(crop, width, height))
            : [{ left: 0, top: 0, right: width, bottom: height }];

        return rectangles.map((rectangle) => ({ ...rectangle, bounds: this.toneBounds(values, rectangle, width) }));
    }

    /** Stesso arrotondamento del crop del backend, sulle dimensioni del canvas. */
    private toneRect(crop: ImageCrop, width: number, height: number): ToneRect {
        const left = this.clamp(Math.round(crop.x * width), 0, Math.max(0, width - 1));
        const top = this.clamp(Math.round(crop.y * height), 0, Math.max(0, height - 1));

        return {
            left,
            top,
            right: left + this.clamp(Math.round(crop.width * width), 1, width - left),
            bottom: top + this.clamp(Math.round(crop.height * height), 1, height - top)
        };
    }

    private toneBounds(values: Uint8ClampedArray, rectangle: ToneRect, width: number): ToneBounds {
        if (!this.recipe.autoContrast) return { min: 255, max: 0 };
        let min = 255;
        let max = 0;
        for (let y = rectangle.top; y < rectangle.bottom; y++) {
            for (let x = rectangle.left; x < rectangle.right; x++) {
                const index = (y * width + x) * 4;
                const luminance = this.luminance(values[index], values[index + 1], values[index + 2]);
                min = Math.min(min, luminance);
                max = Math.max(max, luminance);
            }
        }

        return { min, max };
    }

    private applyPixel(values: Uint8ClampedArray, index: number, bounds: ToneBounds, factor: number, offset: number): void {
        if (this.recipe.grayscale || this.recipe.threshold !== null) {
            const adjusted = this.adjustValue(this.luminance(values[index], values[index + 1], values[index + 2]), bounds, factor, offset);
            const result = this.recipe.threshold === null ? adjusted : adjusted >= this.recipe.threshold ? 255 : 0;
            values[index] = values[index + 1] = values[index + 2] = result;
            return;
        }

        values[index] = this.adjustValue(values[index], bounds, factor, offset);
        values[index + 1] = this.adjustValue(values[index + 1], bounds, factor, offset);
        values[index + 2] = this.adjustValue(values[index + 2], bounds, factor, offset);
    }

    /** Stessa aritmetica intera del backend, per non introdurre differenze di un livello. */
    private adjustValue(value: number, bounds: ToneBounds, factor: number, offset: number): number {
        const stretched = this.recipe.autoContrast && bounds.max > bounds.min ? Math.trunc(((value - bounds.min) * 255) / (bounds.max - bounds.min)) : value;

        return this.clamp(Math.round((stretched - 128) * factor + 128) + offset, 0, 255);
    }

    private luminance(red: number, green: number, blue: number): number {
        return Math.trunc((red * 299 + green * 587 + blue * 114) / 1000);
    }

    private isZeroAngle(degrees: number): boolean {
        return this.normalizeAngle(degrees) < 0.001;
    }

    private normalizeAngle(degrees: number): number {
        return ((degrees % 360) + 360) % 360;
    }

    private clamp(value: number, minimum: number, maximum: number): number {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private startDrag(mode: CropDragMode, mx: number, my: number): void {
        this.dragMode = mode;
        this.dragStartPos = { x: mx, y: my };
        this.cropRectAtDragStart = this.cropRect ? { ...this.cropRect } : null;
        this.isDragging = true;
    }

    private computeResizedRect(mode: CropDragMode, base: CropRect, mx: number, my: number): CropRect {
        const { x, y, width, height } = base;
        let fixedX: number, fixedY: number;

        const cx = Math.min(Math.max(mx, 0), 1);
        const cy = Math.min(Math.max(my, 0), 1);

        switch (mode) {
            case 'resize-nw':
                fixedX = x + width;
                fixedY = y + height;
                break;
            case 'resize-ne':
                fixedX = x;
                fixedY = y + height;
                break;
            case 'resize-sw':
                fixedX = x + width;
                fixedY = y;
                break;
            case 'resize-se':
                fixedX = x;
                fixedY = y;
                break;

            case 'resize-n': {
                const bottom = y + height;
                return { x, width, y: Math.min(bottom, cy), height: Math.abs(bottom - cy) };
            }
            case 'resize-s': {
                return { x, width, y: Math.min(y, cy), height: Math.abs(cy - y) };
            }
            case 'resize-w': {
                const right = x + width;
                return { y, height, x: Math.min(right, cx), width: Math.abs(right - cx) };
            }
            case 'resize-e': {
                return { y, height, x: Math.min(x, cx), width: Math.abs(cx - x) };
            }

            default:
                return base;
        }

        return {
            x: Math.min(fixedX, cx),
            y: Math.min(fixedY, cy),
            width: Math.abs(fixedX - cx),
            height: Math.abs(fixedY - cy)
        };
    }

    private computeCursor(mx: number, my: number, imgRect: DOMRect): string {
        if (!this.cropRect) return 'default';

        const hx = HANDLE_HIT_PX / imgRect.width;
        const hy = HANDLE_HIT_PX / imgRect.height;
        const { x, y, width, height } = this.cropRect;

        if (Math.abs(mx - x) < hx && Math.abs(my - y) < hy) return 'nw-resize';
        if (Math.abs(mx - (x + width)) < hx && Math.abs(my - y) < hy) return 'ne-resize';
        if (Math.abs(mx - x) < hx && Math.abs(my - (y + height)) < hy) return 'sw-resize';
        if (Math.abs(mx - (x + width)) < hx && Math.abs(my - (y + height)) < hy) return 'se-resize';
        if (Math.abs(my - y) < hy && mx > x + hx && mx < x + width - hx) return 'n-resize';
        if (Math.abs(my - (y + height)) < hy && mx > x + hx && mx < x + width - hx) return 's-resize';
        if (Math.abs(mx - x) < hx && my > y + hy && my < y + height - hy) return 'w-resize';
        if (Math.abs(mx - (x + width)) < hx && my > y + hy && my < y + height - hy) return 'e-resize';
        if (mx >= x && mx <= x + width && my >= y && my <= y + height) return 'move';
        return 'default';
    }

    private cropAt(x: number, y: number): number {
        for (let index = this.pageCrops.length - 1; index >= 0; index--) {
            const crop = this.pageCrops[index];
            if (x >= crop.x && x <= crop.x + crop.width && y >= crop.y && y <= crop.y + crop.height) return index;
        }
        return -1;
    }

    private normalize(event: PointerEvent, rect: DOMRect): [number, number] {
        return [Math.min(Math.max((event.clientX - rect.left) / rect.width, 0), 1), Math.min(Math.max((event.clientY - rect.top) / rect.height, 0), 1)];
    }

    private capturePointer(event: PointerEvent): void {
        (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    }

    private releasePointer(event: PointerEvent): void {
        const target = event.currentTarget as HTMLElement;
        if (target.hasPointerCapture(event.pointerId)) target.releasePointerCapture(event.pointerId);
    }

    private getImgAndRect(): { img: HTMLCanvasElement | null; rect: DOMRect | null } {
        const img = this.previewCanvasRef?.nativeElement ?? null;
        return { img, rect: img?.getBoundingClientRect() ?? null };
    }
}
