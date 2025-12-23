import { HttpHeaders } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Popover } from 'primeng/popover';
import { Table } from 'primeng/table';
import { delay, first, firstValueFrom } from 'rxjs';
import { TypeHandlerComponent } from "../../../components/type-handler/type-handler.component";
import { EditScoreDialogComponent } from '../../../dialogs/edit-score-dialog/edit-score-dialog.component';
import { ImportsModule } from '../../../imports';
import { ChildrenEntities, Instruments, InstrumentsCriteria, SheetsMusic, Tracks } from '../../../module';
import { InstrumentsService, KeycloakService, MediaService, ToastService, TracksService } from '../../../service';

@Component({
    selector: 'app-track-detail',
    imports: [
        ImportsModule,
        TypeHandlerComponent,
    ],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [
        TracksService,
        InstrumentsService,
        KeycloakService,
        DialogService,
    ],
    changeDetection: ChangeDetectionStrategy.Default,
})
export class DetailComponent implements OnInit {
    protected track: Tracks = new Tracks();
    protected cols: string[];
    protected selectedScores: SheetsMusic[];
    protected images: string[];
    protected displayGalleria: boolean;
    protected responsiveOptions: any[] = [
        {
            breakpoint: '1024px',
            numVisible: 5
        },
        {
            breakpoint: '960px',
            numVisible: 4
        },
        {
            breakpoint: '768px',
            numVisible: 3
        }
    ];

    private instruments: Instruments[];
    private draggedMedia?: ChildrenEntities = undefined;
    private startDraggedScore?: SheetsMusic = undefined;

    constructor(
        private readonly tracksService: TracksService,
        private readonly mediaService: MediaService,
        private readonly instrumentsService: InstrumentsService,
        private readonly keycloakService: KeycloakService,
        private readonly toastService: ToastService,
        private readonly routeService: ActivatedRoute,
        private readonly dialogService: DialogService,
    ) {
        this.cols = ["Ordine", "Media", "Strumenti"];
        this.selectedScores = [];
        this.images = [];
        this.displayGalleria = false;
        this.instruments = [];
    }

    ngOnInit() {
        this.routeService.params.pipe(first()).subscribe(params => {
            this.loadElement(params['id']);
        });

        let page = 0;
        const instrumentsCriteria: InstrumentsCriteria = { page: page, sort: ['name.keyword,asc'] };

        this.instrumentsService.getAll().pipe(first()).subscribe(async result => {
            let totalElements = result.totalElements;
            this.instruments = result.content;

            while (totalElements > this.instruments.length) {
                instrumentsCriteria.page = ++page;

                const data = await firstValueFrom(this.instrumentsService.getAll(instrumentsCriteria));
                this.instruments.push(...data.content);
                totalElements = data.totalElements;
            }
        });
    }

    protected save(): void {
        this.tracksService.update(this.track.id, this.track).pipe(delay(1000), first()).subscribe({
            next: (track: Tracks) => {
                this.toastService.success("Successo", "Traccia aggiornata con successo");
                this.loadElement(track.id);
            }
        });
    }

    protected trackStream(): string {
        return this.tracksService.stream(this.track.id);
    }

    protected httpHeaders(): HttpHeaders {
        return new HttpHeaders({ 'Authorization': `Bearer ${this.keycloakService.token}` });
    }

    protected addNew(): void {
        this.track.scores ??= [];

        const score = new SheetsMusic();
        const max = Math.max(...this.track.scores.map(score => score.order!), 0);
        score.order = max + 1;
        score.media = [];
        score.instruments = [];

        this.track.scores.push(score);
    }

    protected deleteSelectedTracks(): void {
        for (let selectedScore of this.selectedScores) {
            this.deleteScore(selectedScore);
        }
        this.selectedScores = [];
    }

    protected onGlobalFilter(table: Table<SheetsMusic>, event: Event): void {
        table.filterGlobal((event.target as HTMLInputElement).value, 'contains');
    }

    protected showMedia(media: ChildrenEntities[]) {
        this.displayGalleria = true;
        this.images = media.map(m => this.mediaService.stream(m.index));
    }

    protected toggleDataTable(op: Popover, event: any) {
        op.toggle(event);
    }

    protected dragStart(startDraggedScore: SheetsMusic, media: ChildrenEntities) {
        this.startDraggedScore = startDraggedScore;
        this.draggedMedia = media;
    }

    protected drop(endDraggedScore: SheetsMusic) {
        if (this.draggedMedia) {
            endDraggedScore.media?.push(this.draggedMedia);
            this.startDraggedScore?.media?.splice(this.startDraggedScore.media.findIndex(m => m.index === this.draggedMedia!.index), 1);
            this.startDraggedScore = undefined;
            this.draggedMedia = undefined;
        }
    }

    protected dragEnd() {
        this.startDraggedScore = undefined;
        this.draggedMedia = undefined;
    }

    protected mediaStream(media: ChildrenEntities): string {
        return this.mediaService.stream(media.index);
    }

    protected editScore(score: SheetsMusic): void {
        const dynamicDialogRef: DynamicDialogRef = this.dialogService.open(EditScoreDialogComponent, {
            inputValues: {
                currentScoreOrder: score.order,
                scores: structuredClone(this.track.scores),
                instruments: this.instruments,
            },
            header: "Modifica parte",
            closable: true,
            draggable: true,
            resizable: true,
            modal: true,
            width: '50vw',
            breakpoints: { '1199px': '75vw', '575px': '90vw' },
        });

        dynamicDialogRef.onClose.pipe(first()).subscribe((result: SheetsMusic[]) => {
            if (result) {
                this.track.scores = result;
            }
        });
    }

    protected deleteScore(selectedScore: SheetsMusic): void {
        this.track.scores?.splice(this.track.scores.findIndex(score => selectedScore.order === score.order), 1);
        this.track.scores?.sort((a, b) => a.order! < b.order! ? -1 : 1).forEach((score, i) => score.order = i + 1);
    }

    private loadElement(id: string) {
        this.tracksService.getById(id).pipe(first()).subscribe(track => {
            this.track = track;
        });
    }
}
