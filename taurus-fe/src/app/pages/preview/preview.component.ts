import { Location } from '@angular/common';
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
    protected displayGalleria: boolean = false;
    protected filtersOpen: boolean = false;
    protected currentPage: number = 1;
    protected zoom: number = 100;
    protected previewTitle: string;
    protected previewSource: string;
    protected readonly responsiveOptions = [
        { breakpoint: '1024px', numVisible: 5 },
        { breakpoint: '960px', numVisible: 4 },
        { breakpoint: '768px', numVisible: 3 },
    ];

    constructor(private readonly printerService: PrinterService, private readonly mediaService: MediaService,
        private readonly router: Router, private readonly location: Location) {
        this.mediaStreams = [];
        this.instruments = {};
        this.selectedInstruments = {};
        this.selectAll = true;
        this.previewTitle = 'Spartiti selezionati';
        this.previewSource = 'Traccia';
    }

    ngOnInit() {
        this.previewTitle = this.printerService.previewTitle;
        this.previewSource = this.printerService.previewSource;

        this.printerService.scores.forEach(score => {
            score.media?.forEach(media => {
                this.mediaStreams.push(this.mediaService.stream(media.index));
            });

            if (!score.instruments?.length) {
                this.instruments["null"] = "Senza strumento";
            }

            score.instruments?.forEach(instrument => {
                this.instruments[instrument.index] = instrument.name!;
            });
        });

        this.selectedInstruments = this.getInstrumentIndices(true);

        if (this.mediaStreams.length === 0) {
            this.router.navigate(["/"])
        }
    }

    ngOnDestroy(): void {
        this.printerService.clear();
    }

    protected selectAllChange(event: CheckboxChangeEvent): void {
        this.selectedInstruments = this.getInstrumentIndices(event.checked);
        this.refreshMediaStreams();
    }

    protected selectedInstrumentChange(): void {
        this.selectAll = Object.keys(this.instruments).every(key => this.selectedInstruments[key]);
        this.refreshMediaStreams();
    }

    protected print(): void {
        window.print();
    }

    protected openGalleria(): void {
        this.displayGalleria = true;
    }

    protected goBack(): void {
        this.location.back();
    }

    protected toggleFilters(): void {
        this.filtersOpen = !this.filtersOpen;
    }

    protected previousPage(): void {
        this.currentPage = Math.max(1, this.currentPage - 1);
    }

    protected nextPage(): void {
        this.currentPage = Math.min(this.mediaStreams.length, this.currentPage + 1);
    }

    protected pageChange(event: Event): void {
        const value = Number((event.target as HTMLInputElement).value);
        this.currentPage = Math.min(this.mediaStreams.length, Math.max(1, value || 1));
    }

    protected zoomOut(): void {
        this.zoom = Math.max(60, this.zoom - 10);
    }

    protected zoomIn(): void {
        this.zoom = Math.min(140, this.zoom + 10);
    }

    protected fitPage(): void {
        this.zoom = 100;
    }

    protected get currentMediaStream(): string | undefined {
        return this.mediaStreams[this.currentPage - 1];
    }

    protected get selectedInstrumentCount(): number {
        return Object.values(this.selectedInstruments).filter(Boolean).length;
    }

    protected get instrumentCount(): number {
        return Object.keys(this.instruments).length;
    }

    protected instrumentPageCount(instrumentIndex: string): number {
        return this.printerService.scores
            .filter(score => instrumentIndex === 'null'
                ? !score.instruments?.length
                : score.instruments?.some(instrument => instrument.index.toString() === instrumentIndex))
            .reduce((total, score) => total + (score.media?.length || 0), 0);
    }

    private getInstrumentIndices(value: boolean): { [key: string]: boolean } {
        const instr: { [key: string]: boolean } = {};
        Object.keys(this.instruments).forEach(key => instr[key] = value);

        return instr;
    }

    private filterMedia(): string[] {
        return this.printerService.scores.filter(score => {
            if (!score.instruments?.length) {
                return this.selectedInstruments["null"];
            }

            return score.instruments?.some(instrument => this.selectedInstruments[instrument.index])
        }).flatMap(score => {
            return score.media!.map(media => this.mediaService.stream(media.index));
        });
    }

    private refreshMediaStreams(): void {
        this.mediaStreams = this.filterMedia();
        this.currentPage = Math.min(Math.max(this.currentPage, 1), Math.max(this.mediaStreams.length, 1));
    }
}
