import { Component, ElementRef, inject, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { NgxExtendedPdfViewerModule, NgxExtendedPdfViewerService, PagesLoadedEvent, PDFExportScaleFactor } from 'ngx-extended-pdf-viewer';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { BadgeModule } from 'primeng/badge';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService } from 'primeng/api';
import { PdfAnnotations, PdfCropRegion } from '../../module/pdf-annotations.module';

interface CropRect {
    x: number;
    y: number;
    width: number;
    height: number;
}

type CropDragMode = 'draw' | 'move' | 'resize-nw' | 'resize-ne' | 'resize-sw' | 'resize-se' | 'resize-n' | 'resize-s' | 'resize-e' | 'resize-w';

/** Pixels used as hit-test radius around each corner handle. */
const HANDLE_HIT_PX = 12;

@Component({
    selector: 'app-pdf-manipulator-dialog',
    standalone: true,
    imports: [
        CommonModule,
        NgxExtendedPdfViewerModule,
        ButtonModule,
        TooltipModule,
        BadgeModule,
        ConfirmDialogModule,
    ],
    providers: [NgxExtendedPdfViewerService, ConfirmationService],
    templateUrl: './pdf-manipulator-dialog.component.html',
    styleUrl: './pdf-manipulator-dialog.component.scss',
})
export class PdfManipulatorDialogComponent {
    @ViewChild('cropContainer') cropContainerRef?: ElementRef<HTMLElement>;

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
    protected cropCursor = 'crosshair';
    protected pageCrops: PdfCropRegion[] = [];
    protected editingCrop = false;

    private dragMode: CropDragMode = 'draw';
    private cropRectAtDragStart: CropRect | null = null;
    private dragStartPos: { x: number; y: number } | null = null;
    private isDragging = false;

    private readonly confirmationService = inject(ConfirmationService);

    constructor(
        private readonly dialogRef: DynamicDialogRef,
        private readonly config: DynamicDialogConfig,
        private readonly pdfViewerService: NgxExtendedPdfViewerService,
    ) {
        this.pdfFile = this.config.data.file;
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

    protected hasCrop(page: number): boolean {
        return (this.cropRegions.get(page)?.length ?? 0) > 0;
    }

    protected cropCountForPage(page: number): number {
        return this.cropRegions.get(page)?.length ?? 0;
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
        await new Promise(resolve => setTimeout(resolve, 150));

        const scale: PDFExportScaleFactor = { scale: 1.5 };
        const image = await this.pdfViewerService.getPageAsImage(pageNum, scale);
        this.cropLoading = false;

        if (!image) return;

        this.cropImage = image;
        this.cropPageNum = pageNum;
        this.pageCrops = (this.cropRegions.get(pageNum) ?? []).map(r => ({ ...r }));
        this.cropRect = null;
        this.cropMode = true;
        this.cropCursor = 'crosshair';
    }

    protected exitCropMode(): void {
        this.cropMode = false;
        this.cropPageNum = null;
        this.cropImage = undefined;
        this.cropRect = null;
        this.pageCrops = [];
        this.editingCrop = false;
        this.dragStartPos = null;
        this.cropRectAtDragStart = null;
        this.isDragging = false;
        this.cropCursor = 'crosshair';
    }

    protected applyCrop(): void {
        if (!this.cropRect || this.cropPageNum === null) return;
        const isSignificant = this.cropRect.width > 0.02 && this.cropRect.height > 0.02;
        if (!isSignificant) return;
        const newCrop: PdfCropRegion = { page: this.cropPageNum, ...this.cropRect };
        this.pageCrops = [...this.pageCrops, newCrop];
        this.cropRegions.set(this.cropPageNum, this.pageCrops.map(c => ({ ...c, page: this.cropPageNum! })));
        this.cropRegions = new Map(this.cropRegions);
        this.cropRect = null;
        this.editingCrop = false;
    }

    protected editPageCrop(index: number): void {
        const crop = this.pageCrops[index];
        this.cropRect = { x: crop.x, y: crop.y, width: crop.width, height: crop.height };
        this.editingCrop = true;
        this.removePageCrop(index);
    }

    protected removeCrop(): void {
        if (this.cropPageNum !== null) {
            this.cropRegions.delete(this.cropPageNum);
            this.cropRegions = new Map(this.cropRegions);
            this.pageCrops = [];
            this.editingCrop = false;
        }
    }

    protected removePageCrop(index: number, event?: Event): void {
        event?.stopPropagation();
        this.pageCrops = this.pageCrops.filter((_, i) => i !== index);
        if (this.cropPageNum !== null) {
            if (this.pageCrops.length > 0) {
                this.cropRegions.set(this.cropPageNum, this.pageCrops.map(c => ({ ...c, page: this.cropPageNum! })));
            } else {
                this.cropRegions.delete(this.cropPageNum);
            }
            this.cropRegions = new Map(this.cropRegions);
        }
    }

    protected removeCropFromAll(): void {
        this.cropRegions = new Map();
    }

    protected applyCropToAll(): void {
        if (this.cropPageNum === null) return;
        const cropsToApply = [...this.pageCrops];
        if (this.cropRect) {
            const isSignificant = this.cropRect.width > 0.02 && this.cropRect.height > 0.02;
            if (isSignificant) cropsToApply.push({ page: this.cropPageNum, ...this.cropRect });
        }
        if (cropsToApply.length === 0) return;
        const newMap = new Map<number, PdfCropRegion[]>();
        for (const page of this.pages) {
            newMap.set(page, cropsToApply.map(c => ({ ...c, page })));
        }
        this.cropRegions = newMap;
        this.exitCropMode();
    }

    // ── Mouse handlers ──────────────────────────────────────────────────────────

    protected onCropMouseDown(event: MouseEvent): void {
        const { img, rect } = this.getImgAndRect();
        if (!img || !rect) return;

        const [mx, my] = this.normalize(event, rect);

        if (this.cropRect) {
            const hx = HANDLE_HIT_PX / rect.width;
            const hy = HANDLE_HIT_PX / rect.height;
            const { x, y, width, height } = this.cropRect;

            const corners: Array<[CropDragMode, number, number]> = [
                ['resize-nw', x,         y],
                ['resize-ne', x + width, y],
                ['resize-sw', x,         y + height],
                ['resize-se', x + width, y + height],
            ];

            for (const [mode, cx, cy] of corners) {
                if (Math.abs(mx - cx) < hx && Math.abs(my - cy) < hy) {
                    this.startDrag(mode, mx, my);
                    event.preventDefault();
                    return;
                }
            }

            // Edge hit-tests (checked after corners to avoid overlap)
            const edges: Array<[CropDragMode, boolean]> = [
                ['resize-n', Math.abs(my - y)          < hy && mx > x + hx && mx < x + width - hx],
                ['resize-s', Math.abs(my - (y+height)) < hy && mx > x + hx && mx < x + width - hx],
                ['resize-w', Math.abs(mx - x)          < hx && my > y + hy && my < y + height - hy],
                ['resize-e', Math.abs(mx - (x+width))  < hx && my > y + hy && my < y + height - hy],
            ];

            for (const [mode, hit] of edges) {
                if (hit) {
                    this.startDrag(mode, mx, my);
                    event.preventDefault();
                    return;
                }
            }

            if (mx >= x && mx <= x + width && my >= y && my <= y + height) {
                this.startDrag('move', mx, my);
                event.preventDefault();
                return;
            }
        }

        // Draw new selection
        this.startDrag('draw', mx, my);
        this.cropRect = null;
        event.preventDefault();
    }

    protected onCropMouseMove(event: MouseEvent): void {
        const { img, rect } = this.getImgAndRect();
        if (!img || !rect) return;

        const [mx, my] = this.normalize(event, rect);

        if (!this.isDragging) {
            this.cropCursor = this.computeCursor(mx, my, rect);
            return;
        }

        switch (this.dragMode) {
            case 'draw':
                if (this.dragStartPos) {
                    this.cropRect = {
                        x: Math.min(this.dragStartPos.x, mx),
                        y: Math.min(this.dragStartPos.y, my),
                        width: Math.abs(mx - this.dragStartPos.x),
                        height: Math.abs(my - this.dragStartPos.y),
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
                        y: Math.min(Math.max(this.cropRectAtDragStart.y + dy, 0), 1 - this.cropRectAtDragStart.height),
                    };
                }
                break;

            default:
                if (this.cropRectAtDragStart) {
                    this.cropRect = this.computeResizedRect(this.dragMode, this.cropRectAtDragStart, mx, my);
                }
        }
    }

    protected onCropMouseUp(): void {
        this.isDragging = false;
        this.cropRectAtDragStart = null;
    }

    // ── Styles ──────────────────────────────────────────────────────────────────

    protected get cropSelectionStyle(): Record<string, string> {
        if (!this.cropRect) return {};
        return {
            left:   `${this.cropRect.x * 100}%`,
            top:    `${this.cropRect.y * 100}%`,
            width:  `${this.cropRect.width * 100}%`,
            height: `${this.cropRect.height * 100}%`,
        };
    }

    protected existingCropStyle(crop: PdfCropRegion): Record<string, string> {
        return {
            left:   `${crop.x * 100}%`,
            top:    `${crop.y * 100}%`,
            width:  `${crop.width * 100}%`,
            height: `${crop.height * 100}%`,
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

    // ── Dialog actions ───────────────────────────────────────────────────────────

    protected confirm(): void {
        const annotations: PdfAnnotations = {
            excludedPages: Array.from(this.excludedPages),
            cropRegions: Array.from(this.cropRegions.values()).flat(),
        };
        this.dialogRef.close(annotations);
    }

    protected cancel(): void {
        const hasChanges = this.excludedPages.size > 0 || this.cropRegions.size > 0;
        if (!hasChanges) {
            this.dialogRef.close(null);
            return;
        }
        this.confirmationService.confirm({
            message: 'Hai delle modifiche non salvate che andranno perse. Vuoi uscire comunque?',
            header: 'Uscire senza salvare?',
            icon: 'pi pi-exclamation-triangle',
            rejectButtonProps: {
                label: 'Continua a modificare',
                severity: 'secondary',
                outlined: true,
            },
            acceptButtonProps: {
                label: 'Esci senza salvare',
                severity: 'danger',
            },
            accept: () => this.dialogRef.close(null),
        });
    }

    // ── Private helpers ──────────────────────────────────────────────────────────

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
            case 'resize-nw': fixedX = x + width; fixedY = y + height; break;
            case 'resize-ne': fixedX = x;         fixedY = y + height; break;
            case 'resize-sw': fixedX = x + width; fixedY = y;          break;
            case 'resize-se': fixedX = x;         fixedY = y;          break;

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

            default: return base;
        }

        return {
            x:      Math.min(fixedX, cx),
            y:      Math.min(fixedY, cy),
            width:  Math.abs(fixedX - cx),
            height: Math.abs(fixedY - cy),
        };
    }

    private computeCursor(mx: number, my: number, imgRect: DOMRect): string {
        if (!this.cropRect) return 'crosshair';

        const hx = HANDLE_HIT_PX / imgRect.width;
        const hy = HANDLE_HIT_PX / imgRect.height;
        const { x, y, width, height } = this.cropRect;

        if (Math.abs(mx - x)         < hx && Math.abs(my - y)          < hy) return 'nw-resize';
        if (Math.abs(mx - (x+width)) < hx && Math.abs(my - y)          < hy) return 'ne-resize';
        if (Math.abs(mx - x)         < hx && Math.abs(my - (y+height)) < hy) return 'sw-resize';
        if (Math.abs(mx - (x+width)) < hx && Math.abs(my - (y+height)) < hy) return 'se-resize';
        if (Math.abs(my - y)          < hy && mx > x + hx && mx < x + width - hx)  return 'n-resize';
        if (Math.abs(my - (y+height)) < hy && mx > x + hx && mx < x + width - hx)  return 's-resize';
        if (Math.abs(mx - x)          < hx && my > y + hy && my < y + height - hy)  return 'w-resize';
        if (Math.abs(mx - (x+width))  < hx && my > y + hy && my < y + height - hy)  return 'e-resize';
        if (mx >= x && mx <= x + width && my >= y && my <= y + height)               return 'move';
        return 'crosshair';
    }

    private normalize(event: MouseEvent, rect: DOMRect): [number, number] {
        return [
            Math.min(Math.max((event.clientX - rect.left) / rect.width,  0), 1),
            Math.min(Math.max((event.clientY - rect.top)  / rect.height, 0), 1),
        ];
    }

    private getImgAndRect(): { img: HTMLImageElement | null; rect: DOMRect | null } {
        const img = this.cropContainerRef?.nativeElement?.querySelector<HTMLImageElement>('img') ?? null;
        return { img, rect: img?.getBoundingClientRect() ?? null };
    }
}
