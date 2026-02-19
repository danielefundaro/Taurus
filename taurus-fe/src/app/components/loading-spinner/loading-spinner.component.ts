import { NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { LoadingService } from '../../service';

@Component({
    selector: 'app-loading-spinner',
    imports: [
        NgIf,
        ProgressSpinnerModule,
    ],
    templateUrl: "./loading-spinner.component.html",
    styleUrl: './loading-spinner.component.scss',
    changeDetection: ChangeDetectionStrategy.Default,
})
export class LoadingSpinnerComponent {
    protected isLoading: boolean;

    constructor(private readonly loadingService: LoadingService) {
        this.isLoading = false;

        this.loadingService.loading.subscribe(value => {
            this.isLoading = value;
        });
    }
}
