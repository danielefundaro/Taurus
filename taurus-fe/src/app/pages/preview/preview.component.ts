import { ChangeDetectionStrategy, Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { CheckboxChangeEvent } from 'primeng/checkbox';
import { ImportsModule } from '../../imports';
import { MediaService, PrinterService } from '../../service';

@Component({
    selector: 'app-preview',
    imports: [
        ImportsModule,
    ],
    templateUrl: './preview.component.html',
    styleUrl: './preview.component.scss',
    providers: [
        MediaService,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PreviewComponent implements OnInit, OnDestroy {

    protected mediaStreams: string[];
    protected instruments: { [key: string]: string };
    protected selectedInstruments: { [key: string]: boolean };
    protected selectAll: boolean;

    constructor(private readonly printerService: PrinterService, private readonly mediaService: MediaService,
        private readonly rounter: Router) {
        this.mediaStreams = [];
        this.instruments = {};
        this.selectedInstruments = {};
        this.selectAll = true;
    }

    ngOnInit() {
        this.printerService.scores.forEach(score => {
            score.media?.forEach(media => {
                this.mediaStreams.push(this.mediaService.stream(media.index));
            });

            if (!score.instruments) {
                this.instruments["null"] = "Senza strumento";
            }

            score.instruments?.forEach(instrument => {
                this.instruments[instrument.index] = instrument.name!;
            });
        });

        this.selectedInstruments = this.getInstrumentIndices(true);

        if (this.mediaStreams.length === 0) {
            this.rounter.navigate(["/"])
        }
    }

    ngOnDestroy(): void {
        this.printerService.clear();
    }

    protected selectAllChange(event: CheckboxChangeEvent): void {
        this.selectedInstruments = this.getInstrumentIndices(event.checked);
        this.mediaStreams = this.filterMedia();
    }

    protected selectedInstrumentChange(): void {
        this.selectAll = Object.keys(this.instruments).every(key => this.selectedInstruments[key]);
        this.mediaStreams = this.filterMedia();
    }

    protected print(): void {
        window.print();
    }

    private getInstrumentIndices(value: boolean): { [key: string]: boolean } {
        const instr: { [key: string]: boolean } = {};
        Object.keys(this.instruments).forEach(key => instr[key] = value);

        return instr;
    }

    private filterMedia(): string[] {
        return this.printerService.scores.filter(score => {
            if (!score.instruments) {
                return this.selectedInstruments["null"];
            }

            return score.instruments?.some(instrument => this.selectedInstruments[instrument.index])
        }).flatMap(score => {
            return score.media!.map(media => this.mediaService.stream(media.index));
        });
    }
}
