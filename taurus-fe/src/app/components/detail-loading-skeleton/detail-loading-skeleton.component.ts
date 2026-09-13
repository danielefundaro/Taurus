import { Component } from '@angular/core';
import { SkeletonModule } from 'primeng/skeleton';

@Component({
    selector: 'app-detail-loading-skeleton',
    standalone: true,
    imports: [SkeletonModule],
    templateUrl: './detail-loading-skeleton.component.html'
})
export class DetailLoadingSkeletonComponent { }
