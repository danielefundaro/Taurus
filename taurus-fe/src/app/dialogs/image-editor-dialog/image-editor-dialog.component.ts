import { AfterViewInit, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { catchError, finalize, forkJoin, first, of } from 'rxjs';
import { ImportsModule } from '../../imports';
import { ChildrenEntities, ImageCrop, ImageTransformRecipe, TrackPageAnalysis, TrackPageEditResult } from '../../module';
import { ConfirmService, MediaService, TracksService } from '../../service';

interface ImageEditorData {
    trackId: number;
    trackVersion: number;
    scoreId: number;
    media: ChildrenEntities;
    pageNumber: number;
    scoreTitle: string;
}

type CropHandle = 'nw' | 'n' | 'ne' | 'e' | 'se' | 's' | 'sw' | 'w';

@Component({
    selector: 'app-image-editor-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './image-editor-dialog.component.html',
    styleUrl: './image-editor-dialog.component.scss'
})
export class ImageEditorDialogComponent implements AfterViewInit, OnDestroy {
    @ViewChild('previewCanvas') private canvas?: ElementRef<HTMLCanvasElement>;

    protected readonly data: ImageEditorData;
    protected loading = true;
    protected analyzing = true;
    protected saving = false;
    protected failed = false;
    protected saveError?: string;
    protected analysis?: TrackPageAnalysis;
    protected view: 'edited' | 'original' = 'edited';
    protected thresholdEnabled = false;
    protected activeCropIndex = -1;
    protected cropDrawing = false;
    protected liveMessage = '';
    protected recipe: ImageTransformRecipe;

    private bitmap?: ImageBitmap;
    private cropStart?: { x: number; y: number };
    private cropDraft?: ImageCrop;
    private cropResize?: { cropIndex: number; handle: CropHandle };
    private cropRenderFrame?: number;

    constructor(
        config: DynamicDialogConfig,
        private readonly dialogRef: DynamicDialogRef,
        private readonly tracksService: TracksService,
        private readonly mediaService: MediaService,
        private readonly confirmService: ConfirmService
    ) {
        this.data = config.data as ImageEditorData;
        this.recipe = this.emptyRecipe();
    }

    ngAfterViewInit(): void {
        this.load();
    }

    ngOnDestroy(): void {
        if (this.cropRenderFrame !== undefined) cancelAnimationFrame(this.cropRenderFrame);
        this.bitmap?.close();
    }

    protected get dirty(): boolean {
        return (
            this.recipe.rotationQuarterTurns !== 0 ||
            !this.isZeroAngle(this.recipe.deskewDegrees) ||
            this.recipe.crops.length > 0 ||
            this.recipe.grayscale ||
            this.recipe.brightness !== 0 ||
            this.recipe.contrast !== 0 ||
            this.recipe.autoContrast ||
            this.recipe.threshold !== null
        );
    }

    protected get activeCrop(): ImageCrop | undefined {
        return this.recipe.crops[this.activeCropIndex];
    }

    protected rotate(direction: -1 | 1): void {
        this.recipe.rotationQuarterTurns = (((this.recipe.rotationQuarterTurns + direction) % 4) + 4) % 4;
        this.changed('Immagine ruotata');
    }

    protected toggleThreshold(enabled: boolean): void {
        this.thresholdEnabled = enabled;
        this.recipe.threshold = enabled ? 180 : null;
        this.changed(enabled ? 'Soglia bianco e nero attivata' : 'Soglia bianco e nero disattivata');
    }

    protected startAddingCrop(): void {
        if (this.recipe.crops.length >= 8) return;
        this.cropDraft = undefined;
        this.cropDrawing = true;
        this.view = 'edited';
        this.announce('Trascina sull’immagine per disegnare una zona di ritaglio');
    }

    protected splitCrop(direction: 'vertical' | 'horizontal'): void {
        this.recipe.crops =
            direction === 'vertical'
                ? [
                      { x: 0, y: 0, width: 0.5, height: 1 },
                      { x: 0.5, y: 0, width: 0.5, height: 1 }
                  ]
                : [
                      { x: 0, y: 0, width: 1, height: 0.5 },
                      { x: 0, y: 0.5, width: 1, height: 0.5 }
                  ];
        this.activeCropIndex = 0;
        this.cropDrawing = false;
        this.changed(direction === 'vertical' ? 'Pagina divisa in metà sinistra e destra' : 'Pagina divisa in metà alta e bassa');
    }

    protected selectCrop(index: number): void {
        this.activeCropIndex = index;
        this.cropDrawing = false;
        this.render();
    }

    protected removeCrop(index: number): void {
        this.recipe.crops = this.recipe.crops.filter((_, cropIndex) => cropIndex !== index);
        this.activeCropIndex = this.recipe.crops.length ? Math.min(index, this.recipe.crops.length - 1) : -1;
        this.changed('Zona di ritaglio rimossa');
    }

    protected updateCrop(field: keyof ImageCrop, value: number | null): void {
        if (!this.activeCrop) return;
        const crop = { ...this.activeCrop, [field]: Number(value ?? 0) };
        crop.x = this.clamp(crop.x, 0, 0.98);
        crop.y = this.clamp(crop.y, 0, 0.98);
        crop.width = this.clamp(crop.width, 0.02, 1 - crop.x);
        crop.height = this.clamp(crop.height, 0.02, 1 - crop.y);
        this.recipe.crops = this.recipe.crops.map((current, index) => (index === this.activeCropIndex ? crop : current));
        this.changed('Ritaglio aggiornato');
    }

    protected startCrop(event: PointerEvent): void {
        if (this.view !== 'edited') return;
        const point = this.normalizedPointer(event);
        if (!point) return;
        const canvas = event.currentTarget as HTMLCanvasElement;
        if (this.cropDrawing) {
            this.cropStart = point;
            this.cropDraft = { x: point.x, y: point.y, width: 0, height: 0 };
            canvas.setPointerCapture(event.pointerId);
            event.preventDefault();
            return;
        }

        const handle = this.cropHandleAt(point, canvas);
        if (handle && this.activeCropIndex >= 0) {
            this.cropResize = { cropIndex: this.activeCropIndex, handle };
            canvas.setPointerCapture(event.pointerId);
            canvas.style.cursor = this.cropHandleCursor(handle);
            event.preventDefault();
            return;
        }

        const cropIndex = this.cropAt(point);
        if (cropIndex >= 0 && cropIndex !== this.activeCropIndex) {
            this.activeCropIndex = cropIndex;
            this.render();
            event.preventDefault();
        }
    }

    protected moveCrop(event: PointerEvent): void {
        const canvas = event.currentTarget as HTMLCanvasElement;
        const point = this.normalizedPointer(event);
        if (!point || this.view !== 'edited') return;
        if (this.cropDrawing && this.cropStart) {
            this.cropDraft = this.cropBetween(this.cropStart, point);
            this.scheduleCropRender();
            event.preventDefault();
            return;
        }
        if (this.cropResize) {
            const crop = this.recipe.crops[this.cropResize.cropIndex];
            if (!crop) return;
            const resized = this.resizeCrop(crop, this.cropResize.handle, point);
            this.recipe.crops = this.recipe.crops.map((current, index) => (index === this.cropResize!.cropIndex ? resized : current));
            this.scheduleCropRender();
            event.preventDefault();
            return;
        }
        canvas.style.cursor = this.cropDrawing ? 'crosshair' : this.cropHandleCursor(this.cropHandleAt(point, canvas));
    }

    protected finishCrop(event: PointerEvent): void {
        if (this.cropResize) {
            this.cropResize = undefined;
            this.changed('Zona di ritaglio ridimensionata');
            return;
        }
        if (!this.cropStart || !this.cropDrawing || this.view !== 'edited') return;
        const end = this.normalizedPointer(event);
        const start = this.cropStart;
        this.cropStart = undefined;
        const crop = end ? this.cropBetween(start, end) : undefined;
        this.cropDraft = undefined;
        if (!crop || crop.width < 0.02 || crop.height < 0.02) {
            this.render();
            return;
        }
        this.recipe.crops = [...this.recipe.crops, crop];
        this.activeCropIndex = this.recipe.crops.length - 1;
        this.cropDrawing = false;
        this.changed('Zona di ritaglio aggiunta');
    }

    protected cancelCropDrawing(): void {
        const hadDraft = this.cropDraft !== undefined;
        this.cropStart = undefined;
        this.cropDraft = undefined;
        this.cropResize = undefined;
        if (hadDraft) this.render();
    }

    protected leaveCrop(event: PointerEvent): void {
        if (!this.cropStart && !this.cropResize) (event.currentTarget as HTMLCanvasElement).style.cursor = this.cropDrawing ? 'crosshair' : 'default';
    }

    protected applySuggestions(): void {
        if (!this.analysis) return;
        if (this.analysis.suggestions.includes('AUTO_CROP')) {
            this.recipe.crops = [{ ...this.analysis.contentBounds }];
            this.activeCropIndex = 0;
        }
        if (this.analysis.suggestions.includes('DESKEW')) this.recipe.deskewDegrees = this.normalizeAngle(this.analysis.estimatedSkewDegrees);
        if (this.analysis.suggestions.includes('AUTO_CONTRAST')) this.recipe.autoContrast = true;
        this.changed('Suggerimenti applicati');
    }

    protected reset(): void {
        this.recipe = this.emptyRecipe();
        this.thresholdEnabled = false;
        this.activeCropIndex = -1;
        this.cropDrawing = false;
        this.changed('Modifiche ripristinate');
    }

    protected changed(message?: string): void {
        this.saveError = undefined;
        this.render();
        if (message) this.announce(message);
    }

    protected sliderPosition(value: number | null, minimum: number, maximum: number): number {
        const current = value ?? minimum;
        return ((this.clamp(current, minimum, maximum) - minimum) / (maximum - minimum)) * 100;
    }

    protected save(): void {
        if (!this.dirty || this.saving) return;
        this.saving = true;
        this.saveError = undefined;
        this.tracksService
            .editPage(this.data.trackId, this.data.scoreId, this.data.media.index, this.recipe, crypto.randomUUID())
            .pipe(
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe({
                next: (result: TrackPageEditResult) => this.dialogRef.close(result),
                error: (error: HttpErrorResponse) => {
                    this.saveError = error.status === 409 ? 'La traccia o la pagina è stata modificata. Ricarica la traccia prima di riprovare.' : 'Non è stato possibile salvare l’immagine. La modifica è ancora disponibile.';
                    this.announce(this.saveError);
                }
            });
    }

    protected cancel(): void {
        if (!this.dirty) {
            this.dialogRef.close();
            return;
        }
        this.confirmService.confirmDiscard({
            title: 'Uscire senza salvare?',
            consequence: 'Le regolazioni dell’immagine andranno perse.',
            actionLabel: 'Esci senza salvare',
            accept: () => this.dialogRef.close()
        });
    }

    private load(): void {
        forkJoin({
            image: this.mediaService.streamImage(this.data.media.index),
            analysis: this.tracksService.analyzePage(this.data.trackId, this.data.scoreId, this.data.media.index).pipe(catchError(() => of(null)))
        })
            .pipe(first())
            .subscribe({
                next: async ({ image, analysis }) => {
                    this.analysis = analysis ?? undefined;
                    this.analyzing = false;
                    try {
                        this.bitmap = await createImageBitmap(image);
                        this.loading = false;
                        this.render();
                    } catch {
                        this.loading = false;
                        this.failed = true;
                    }
                },
                error: () => {
                    this.loading = false;
                    this.analyzing = false;
                    this.failed = true;
                }
            });
    }

    private render(): void {
        const canvas = this.canvas?.nativeElement;
        if (!canvas || !this.bitmap) return;
        if (this.view === 'original') {
            this.drawOriginal(canvas);
            return;
        }

        const turns = ((this.recipe.rotationQuarterTurns % 4) + 4) % 4;
        const rotatedWidth = turns % 2 ? this.bitmap.height : this.bitmap.width;
        const rotatedHeight = turns % 2 ? this.bitmap.width : this.bitmap.height;
        const fineRadians = (this.normalizeAngle(this.recipe.deskewDegrees) * Math.PI) / 180;
        const previewScale = Math.min(1, 1400 / Math.max(rotatedWidth, rotatedHeight));
        const oriented = document.createElement('canvas');
        oriented.width = Math.max(1, Math.round(rotatedWidth * previewScale));
        oriented.height = Math.max(1, Math.round(rotatedHeight * previewScale));
        const orientedContext = oriented.getContext('2d', { willReadFrequently: true })!;
        orientedContext.fillStyle = '#fff';
        orientedContext.fillRect(0, 0, oriented.width, oriented.height);
        orientedContext.translate(oriented.width / 2, oriented.height / 2);
        orientedContext.rotate((turns * Math.PI) / 2 + fineRadians);
        orientedContext.drawImage(this.bitmap, (-this.bitmap.width * previewScale) / 2, (-this.bitmap.height * previewScale) / 2, this.bitmap.width * previewScale, this.bitmap.height * previewScale);

        canvas.width = oriented.width;
        canvas.height = oriented.height;
        const context = canvas.getContext('2d', { willReadFrequently: true })!;
        context.drawImage(oriented, 0, 0);
        this.applyTone(context, canvas.width, canvas.height);
        this.drawCropOverlays(context, canvas.width, canvas.height);
        this.drawCropDraft(context, canvas.width, canvas.height);
    }

    private drawOriginal(canvas: HTMLCanvasElement): void {
        const scale = Math.min(1, 1400 / Math.max(this.bitmap!.width, this.bitmap!.height));
        canvas.width = Math.max(1, Math.round(this.bitmap!.width * scale));
        canvas.height = Math.max(1, Math.round(this.bitmap!.height * scale));
        canvas.getContext('2d')!.drawImage(this.bitmap!, 0, 0, canvas.width, canvas.height);
    }

    private applyTone(context: CanvasRenderingContext2D, width: number, height: number): void {
        const image = context.getImageData(0, 0, width, height);
        const values = image.data;
        let min = 255;
        let max = 0;
        if (this.recipe.autoContrast) {
            for (let i = 0; i < values.length; i += 4) {
                const luminance = this.luminance(values[i], values[i + 1], values[i + 2]);
                min = Math.min(min, luminance);
                max = Math.max(max, luminance);
            }
        }
        const factor = 1 + this.recipe.contrast / 100;
        const offset = this.recipe.brightness * 2.55;
        for (let i = 0; i < values.length; i += 4) {
            const luminance = this.luminance(values[i], values[i + 1], values[i + 2]);
            const stretched = this.recipe.autoContrast && max > min ? ((luminance - min) * 255) / (max - min) : luminance;
            const adjusted = this.clamp((stretched - 128) * factor + 128 + offset, 0, 255);
            if (this.recipe.grayscale || this.recipe.threshold !== null) {
                const result = this.recipe.threshold === null ? adjusted : adjusted >= this.recipe.threshold ? 255 : 0;
                values[i] = values[i + 1] = values[i + 2] = result;
            } else {
                values[i] = this.adjustChannel(values[i], min, max, factor, offset);
                values[i + 1] = this.adjustChannel(values[i + 1], min, max, factor, offset);
                values[i + 2] = this.adjustChannel(values[i + 2], min, max, factor, offset);
            }
        }
        context.putImageData(image, 0, 0);
    }

    private emptyRecipe(): ImageTransformRecipe {
        return {
            expectedTrackVersion: this.data.trackVersion,
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

    private luminance(red: number, green: number, blue: number): number {
        return (red * 299 + green * 587 + blue * 114) / 1000;
    }

    private normalizedPointer(event: PointerEvent): { x: number; y: number } | undefined {
        const canvas = this.canvas?.nativeElement;
        if (!canvas) return undefined;
        const rect = canvas.getBoundingClientRect();
        return {
            x: this.clamp((event.clientX - rect.left) / rect.width, 0, 1),
            y: this.clamp((event.clientY - rect.top) / rect.height, 0, 1)
        };
    }

    private adjustChannel(value: number, min: number, max: number, factor: number, offset: number): number {
        const stretched = this.recipe.autoContrast && max > min ? ((value - min) * 255) / (max - min) : value;
        return this.clamp((stretched - 128) * factor + 128 + offset, 0, 255);
    }

    private drawCropOverlays(context: CanvasRenderingContext2D, width: number, height: number): void {
        this.recipe.crops.forEach((crop, index) => {
            const x = crop.x * width;
            const y = crop.y * height;
            const cropWidth = crop.width * width;
            const cropHeight = crop.height * height;
            const active = index === this.activeCropIndex;
            context.save();
            context.fillStyle = active ? 'rgb(16 185 129 / 16%)' : 'rgb(59 130 246 / 12%)';
            context.strokeStyle = active ? '#10b981' : '#3b82f6';
            context.lineWidth = Math.max(2, Math.min(width, height) / 300);
            context.fillRect(x, y, cropWidth, cropHeight);
            context.strokeRect(x, y, cropWidth, cropHeight);
            context.fillStyle = context.strokeStyle;
            context.font = `700 ${Math.max(14, Math.min(width, height) / 28)}px sans-serif`;
            context.textBaseline = 'top';
            context.fillText(String(index + 1), x + 6, y + 4);
            if (active) this.drawCropHandles(context, crop, width, height);
            context.restore();
        });
    }

    private drawCropHandles(context: CanvasRenderingContext2D, crop: ImageCrop, width: number, height: number): void {
        const canvasWidth = this.canvas?.nativeElement.getBoundingClientRect().width ?? width;
        const radius = Math.max(5, (7 * width) / Math.max(1, canvasWidth));
        context.fillStyle = '#fff';
        context.strokeStyle = '#10b981';
        context.lineWidth = Math.max(2, radius / 3);
        for (const point of this.cropHandlePoints(crop)) {
            context.beginPath();
            context.arc(point.x * width, point.y * height, radius, 0, Math.PI * 2);
            context.fill();
            context.stroke();
        }
    }

    private drawCropDraft(context: CanvasRenderingContext2D, width: number, height: number): void {
        if (!this.cropDraft) return;
        const x = this.cropDraft.x * width;
        const y = this.cropDraft.y * height;
        const cropWidth = this.cropDraft.width * width;
        const cropHeight = this.cropDraft.height * height;
        context.save();
        context.fillStyle = 'rgb(245 158 11 / 16%)';
        context.strokeStyle = '#f59e0b';
        context.lineWidth = Math.max(2, Math.min(width, height) / 300);
        context.setLineDash([context.lineWidth * 4, context.lineWidth * 3]);
        context.fillRect(x, y, cropWidth, cropHeight);
        context.strokeRect(x, y, cropWidth, cropHeight);
        context.setLineDash([]);
        context.fillStyle = context.strokeStyle;
        context.font = `700 ${Math.max(14, Math.min(width, height) / 28)}px sans-serif`;
        context.textBaseline = 'top';
        context.fillText(String(this.recipe.crops.length + 1), x + 6, y + 4);
        context.restore();
    }

    private cropHandleAt(point: { x: number; y: number }, canvas: HTMLCanvasElement): CropHandle | undefined {
        if (!this.activeCrop) return undefined;
        const rect = canvas.getBoundingClientRect();
        const tolerance = 14;
        return this.cropHandlePoints(this.activeCrop).find((handle) => Math.abs(handle.x - point.x) * rect.width <= tolerance && Math.abs(handle.y - point.y) * rect.height <= tolerance)?.handle;
    }

    private cropHandlePoints(crop: ImageCrop): Array<{ handle: CropHandle; x: number; y: number }> {
        const left = crop.x;
        const top = crop.y;
        const right = crop.x + crop.width;
        const bottom = crop.y + crop.height;
        const centerX = left + crop.width / 2;
        const centerY = top + crop.height / 2;
        return [
            { handle: 'nw', x: left, y: top },
            { handle: 'ne', x: right, y: top },
            { handle: 'se', x: right, y: bottom },
            { handle: 'sw', x: left, y: bottom },
            { handle: 'n', x: centerX, y: top },
            { handle: 'e', x: right, y: centerY },
            { handle: 's', x: centerX, y: bottom },
            { handle: 'w', x: left, y: centerY }
        ];
    }

    private resizeCrop(crop: ImageCrop, handle: CropHandle, point: { x: number; y: number }): ImageCrop {
        const minimum = 0.02;
        let left = crop.x;
        let top = crop.y;
        let right = crop.x + crop.width;
        let bottom = crop.y + crop.height;
        if (handle.includes('w')) left = this.clamp(point.x, 0, right - minimum);
        if (handle.includes('e')) right = this.clamp(point.x, left + minimum, 1);
        if (handle.includes('n')) top = this.clamp(point.y, 0, bottom - minimum);
        if (handle.includes('s')) bottom = this.clamp(point.y, top + minimum, 1);
        return { x: left, y: top, width: right - left, height: bottom - top };
    }

    private cropBetween(start: { x: number; y: number }, end: { x: number; y: number }): ImageCrop {
        return {
            x: Math.min(start.x, end.x),
            y: Math.min(start.y, end.y),
            width: Math.abs(end.x - start.x),
            height: Math.abs(end.y - start.y)
        };
    }

    private cropAt(point: { x: number; y: number }): number {
        for (let index = this.recipe.crops.length - 1; index >= 0; index--) {
            const crop = this.recipe.crops[index];
            if (point.x >= crop.x && point.x <= crop.x + crop.width && point.y >= crop.y && point.y <= crop.y + crop.height) return index;
        }
        return -1;
    }

    private cropHandleCursor(handle?: CropHandle): string {
        if (!handle) return 'default';
        if (handle === 'n' || handle === 's') return 'ns-resize';
        if (handle === 'e' || handle === 'w') return 'ew-resize';
        if (handle === 'nw' || handle === 'se') return 'nwse-resize';
        return 'nesw-resize';
    }

    private isZeroAngle(degrees: number): boolean {
        return this.normalizeAngle(degrees) < 0.001;
    }

    private normalizeAngle(degrees: number): number {
        return ((degrees % 360) + 360) % 360;
    }

    private scheduleCropRender(): void {
        if (this.cropRenderFrame !== undefined) return;
        this.cropRenderFrame = requestAnimationFrame(() => {
            this.cropRenderFrame = undefined;
            this.render();
        });
    }

    private clamp(value: number, minimum: number, maximum: number): number {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private announce(message: string): void {
        this.liveMessage = '';
        queueMicrotask(() => (this.liveMessage = message));
    }
}
