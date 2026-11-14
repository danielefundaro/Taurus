import { Component, ChangeDetectionStrategy } from '@angular/core';
import { SkeletonModule } from 'primeng/skeleton';

@Component({
    selector: 'app-detail-loading-skeleton',
    standalone: true,
    imports: [SkeletonModule],
    changeDetection: ChangeDetectionStrategy.Eager,
    templateUrl: './detail-loading-skeleton.component.html'
})
export class DetailLoadingSkeletonComponent {}
