import { Component, Input } from '@angular/core';
import { SkeletonModule } from 'primeng/skeleton';
import { ListLayout } from '../../service/list-layout.service';

@Component({
    selector: 'app-list-loading-skeleton',
    standalone: true,
    imports: [SkeletonModule],
    templateUrl: './list-loading-skeleton.component.html'
})
export class ListLoadingSkeletonComponent {
    @Input() layout: ListLayout = 'list';
    protected readonly placeholders = [0, 1, 2, 3, 4, 5];
}
