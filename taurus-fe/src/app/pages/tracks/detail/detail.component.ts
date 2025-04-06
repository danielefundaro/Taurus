import { HttpHeaders } from '@angular/common/http';
import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { Table } from 'primeng/table';
import { first, switchMap } from 'rxjs';
import { ImportsModule } from '../../../imports';
import { Albums, Tracks } from '../../../module';
import { SheetsMusic } from '../../../module/sheets-music.module';
import { KeycloakService, MediaService, TracksService } from '../../../service';
import { TypeHandlerComponent } from "../../../components/type-handler/type-handler.component";

@Component({
    selector: 'app-track-detail',
    imports: [
    ImportsModule,
    TypeHandlerComponent
],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [
        TracksService,
        KeycloakService,
    ],
})
export class DetailComponent {
    protected sortOptions!: SelectItem[];
    protected totalRecords: number = 0;
    protected track: Tracks = new Tracks();
    protected cols: string[];
    protected selectedScores: SheetsMusic[];
    protected images: string[] = [];
    protected displayBasic: boolean = false;
    protected galleriaResponsiveOptions: any[] = [
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
        },
        {
            breakpoint: '560px',
            numVisible: 1
        }
    ];

    constructor(private readonly tracksService: TracksService, private readonly mediaService: MediaService,
        private readonly keycloakService: KeycloakService, private readonly routeService: ActivatedRoute) {
        this.cols = ["Ordine", "Descrizione", "Media", "Strumento"];
        this.selectedScores = [];
    }

    ngOnInit() {
        this.routeService.params.pipe(first()).subscribe(params => {
            this.loadElement(params['id']);
        });
    }

    protected save(): void {
        this.tracksService.update(this.track.id, this.track).pipe(first()).subscribe({
            next: (album: Albums) => {
                this.loadElement(album.id);
            }
        });
    }

    protected trackStream(): string {
        return this.tracksService.stream(this.track.id);
    }

    protected httpHeaders(): HttpHeaders {
        return new HttpHeaders({ 'Authorization': `Bearer ${this.keycloakService.token}` });
    }

    protected deleteSelectedTracks(): void {
        this.selectedScores.forEach(selectedScore => {
            this.deleteScore(selectedScore);
        });
    }

    protected onGlobalFilter(table: Table<SheetsMusic>, event: Event): void {
        table.filterGlobal((event.target as HTMLInputElement).value, 'contains');
    }

    protected showMedia() {
        this.displayBasic = true;
    }

    protected editScore(score: SheetsMusic): void {
        console.log(score);
    }

    protected deleteScore(selectedScore: SheetsMusic): void {
        this.track.scores?.splice(this.track.scores.findIndex(score => selectedScore.order === score.order), 1);
    }

    private loadElement(id: string) {
        this.tracksService.getById(id).pipe(first(), switchMap(track => {
            this.track = track;
            return this.track.scores!.map(score => score.media!.flatMap(media => this.mediaService.stream(media.index)));
        })).subscribe({
            next: (images: string[]) => {
                this.images = images;
            }
        });
    }
}
