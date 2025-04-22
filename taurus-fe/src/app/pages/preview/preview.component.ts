import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ImportsModule } from '../../imports';
import { PrinterService } from '../../service';

@Component({
    selector: 'app-preview',
    imports: [
        ImportsModule,
    ],
    templateUrl: './preview.component.html',
    styleUrl: './preview.component.scss',
    providers: [
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PreviewComponent implements OnInit, OnDestroy {

    protected mediaStreams: string[];

    constructor(private readonly printerService: PrinterService, private readonly rounter: Router) {
        this.mediaStreams = [];
    }

    ngOnInit() {
        this.mediaStreams = this.printerService.mediaStream;

        if (this.mediaStreams.length === 0) {
            this.rounter.navigate(["/"])
        }
    }

    ngOnDestroy(): void {
        this.printerService.clear();
    }

    protected print(): void {
        window.print();
    }
}
