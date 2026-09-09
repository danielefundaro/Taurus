import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, Input, OnChanges, OnDestroy, ViewChild } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { Image, ImageModule } from 'primeng/image';
import { SkeletonModule } from 'primeng/skeleton';
import { Subscription } from 'rxjs';
import { ChildrenEntities } from '../../../../module';
import { MediaService } from '../../../../service';

@Component({
    selector: 'app-media-thumbnail',
    standalone: true,
    imports: [ButtonModule, ImageModule, SkeletonModule],
    templateUrl: './media-thumbnail.component.html',
    styleUrl: './media-thumbnail.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class MediaThumbnailComponent implements AfterViewInit, OnChanges, OnDestroy {
    @Input({ required: true }) media!: ChildrenEntities;
    @Input() alt = '';
    @ViewChild('previewImage') private previewImage?: Image;

    protected imageUrl?: string;
    protected loading = true;
    protected failed = false;

    private observer?: IntersectionObserver;
    private request?: Subscription;
    private loadedMediaId?: number;
    private previewAfterLoad = false;

    constructor(
        private readonly elementRef: ElementRef<HTMLElement>,
        private readonly mediaService: MediaService,
        private readonly changeDetector: ChangeDetectorRef
    ) {}

    ngAfterViewInit(): void {
        if (!('IntersectionObserver' in globalThis)) {
            this.load();
            return;
        }
        this.observer = new IntersectionObserver(
            (entries) => {
                if (entries.some((entry) => entry.isIntersecting)) {
                    this.load();
                    this.observer?.disconnect();
                }
            },
            { rootMargin: '240px' }
        );
        this.observer.observe(this.elementRef.nativeElement);
    }

    ngOnChanges(): void {
        if (this.loadedMediaId !== undefined && this.loadedMediaId !== this.media?.index) this.reset();
    }

    ngOnDestroy(): void {
        this.observer?.disconnect();
        this.request?.unsubscribe();
        this.releaseUrl();
    }

    openPreview(): void {
        if (this.imageUrl && !this.failed) {
            this.openLoadedPreview();
            return;
        }

        this.previewAfterLoad = true;
        this.load();
    }

    protected retry(): void {
        this.reset();
        this.load();
    }

    private load(): void {
        if (!this.media?.index || this.request || this.imageUrl) return;
        this.loading = true;
        this.failed = false;
        this.loadedMediaId = this.media.index;
        this.request = this.mediaService.streamImage(this.media.index).subscribe({
            next: (blob) => {
                this.releaseUrl();
                this.imageUrl = URL.createObjectURL(blob);
                this.loading = false;
                this.request = undefined;
                this.changeDetector.detectChanges();
                if (this.previewAfterLoad) {
                    this.previewAfterLoad = false;
                    this.openLoadedPreview();
                }
            },
            error: () => {
                this.loading = false;
                this.failed = true;
                this.request = undefined;
                this.previewAfterLoad = false;
                this.changeDetector.markForCheck();
            }
        });
    }

    private reset(): void {
        this.request?.unsubscribe();
        this.request = undefined;
        this.releaseUrl();
        this.loadedMediaId = undefined;
        this.previewAfterLoad = false;
        this.loading = true;
        this.failed = false;
    }

    private releaseUrl(): void {
        if (this.imageUrl) URL.revokeObjectURL(this.imageUrl);
        this.imageUrl = undefined;
    }

    private openLoadedPreview(): void {
        this.previewImage?.previewButton?.nativeElement.click();
    }
}
