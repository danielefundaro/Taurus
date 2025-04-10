import { HttpHeaders } from '@angular/common/http';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { Popover } from 'primeng/popover';
import { Table } from 'primeng/table';
import { first } from 'rxjs';
import { TypeHandlerComponent } from "../../../components/type-handler/type-handler.component";
import { ImportsModule } from '../../../imports';
import { ChildrenEntities, Tracks } from '../../../module';
import { SheetsMusic } from '../../../module/sheets-music.module';
import { KeycloakService, MediaService, TracksService } from '../../../service';

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
        KeycloakService,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetailComponent {
    protected sortOptions!: SelectItem[];
    protected totalRecords: number = 0;
    protected track: Tracks = new Tracks();
    protected draggableImage?: ChildrenEntities;
    protected oldScore?: SheetsMusic;
    protected cols: string[];
    protected selectedScores: SheetsMusic[];
    protected images: string[];
    protected displayBasic: boolean;
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

    constructor(private readonly tracksService: TracksService, private readonly mediaService: MediaService,
        private readonly keycloakService: KeycloakService, private readonly routeService: ActivatedRoute) {
        this.cols = ["Ordine", "Descrizione", "Media", "Strumento"];
        this.selectedScores = [];
        this.images = [];
        this.displayBasic = false;
    }

    ngOnInit() {
        this.routeService.params.pipe(first()).subscribe(params => {
            this.loadElement(params['id']);
        });
    }

    protected save(): void {
        this.tracksService.update(this.track.id, this.track).pipe(first()).subscribe({
            next: (track: Tracks) => {
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
        if (!this.track.scores) {
            this.track.scores = [];
        }

        const score = new SheetsMusic();
        const max = Math.max(...this.track.scores.map(score => score.order!), 0);
        score.order = max + 1;

        this.track.scores.push(score);
    }

    protected deleteSelectedTracks(): void {
        this.selectedScores.forEach(selectedScore => {
            this.deleteScore(selectedScore);
        });
    }

    protected onGlobalFilter(table: Table<SheetsMusic>, event: Event): void {
        table.filterGlobal((event.target as HTMLInputElement).value, 'contains');
    }

    protected showMedia(media: ChildrenEntities[]) {
        this.displayBasic = true;
        this.images = media.map(m => this.mediaService.stream(m.index));
    }

    protected toggleDataTable(op: Popover, event: any) {
        op.toggle(event);
    }

    protected mediaStream(media: ChildrenEntities): string {
        return this.mediaService.stream(media.index);
    }

    protected editScore(score: SheetsMusic): void {
        console.log(score);
    }

    protected deleteScore(selectedScore: SheetsMusic): void {
        this.track.scores?.splice(this.track.scores.findIndex(score => selectedScore.order === score.order), 1);
    }

    protected dragstartHandler(media: ChildrenEntities, score: SheetsMusic): void {
        this.draggableImage = JSON.parse(JSON.stringify(media));
        this.oldScore = score;
    }

    protected dragoverHandler(ev: DragEvent): void {
        ev.preventDefault();
    }

    protected dropHandler(score: SheetsMusic): void {
        if (this.draggableImage && this.oldScore && this.oldScore.order !== score.order) {
            if (!score.media) {
                score.media = [];
            }

            score.media.push(this.draggableImage);
            this.oldScore.media?.splice(this.oldScore.media.findIndex(media => media.index === this.draggableImage!.index), 1);
            this.draggableImage = undefined;
            this.oldScore = undefined;
        }
    }

    private loadElement(id: string) {
        this.tracksService.getById(id).pipe(first()).subscribe(track => {
            this.track = track;
        });
    }
}
