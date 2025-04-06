import { Component } from '@angular/core';
import { AlbumsService, MediaService, TracksService } from '../../../service';
import { SelectItem } from 'primeng/api';
import { ActivatedRoute } from '@angular/router';
import { Albums, ChildrenEntities, Tracks } from '../../../module';
import { ImportsModule } from '../../../imports';
import { Table } from 'primeng/table';
import { first } from 'rxjs';
import { SheetsMusic } from '../../../module/sheets-music.module';

@Component({
    selector: 'app-track-detail',
    imports: [
        ImportsModule,
    ],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [AlbumsService, TracksService],
})
export class DetailComponent {
    public sortOptions!: SelectItem[];
    public totalRecords: number = 0;
    public track: Tracks = new Tracks();
    public cols: string[];
    public selectedTracks: ChildrenEntities[];

    constructor(private readonly tracksService: TracksService, private readonly mediaService: MediaService,
        private readonly routeService: ActivatedRoute) {
        this.cols = ["Codice", "Ordine", "Nome"];
        this.selectedTracks = [];
    }

    ngOnInit() {
        this.routeService.params.pipe(first()).subscribe(params => {
            this.loadElement(params['id']);
        });
    }

    public save(): void {
        this.tracksService.update(this.track.id, this.track).pipe(first()).subscribe({
            next: (album: Albums) => {
                this.loadElement(album.id);
            }
        });
    }

    public deleteSelectedTracks(): void {
        this.selectedTracks.forEach(selectedTrack => {
            this.deleteTrack(selectedTrack);
        });
    }

    public addNew(): void {
        this.track.scores?.push(new SheetsMusic());
    }

    public onGlobalFilter(table: Table<SheetsMusic>, event: Event): void {
        table.filterGlobal((event.target as HTMLInputElement).value, 'contains');
    }

    public editTrack(track: ChildrenEntities): void {
        console.log(track);
    }

    public deleteTrack(selectedScore: SheetsMusic): void {
        this.track.scores?.slice(this.track.scores.findIndex(score => selectedScore.order === score.order), 1);
    }

    private loadElement(id: string) {
        this.tracksService.getById(id).pipe(first()).subscribe({
            next: (album: Albums) => {
                this.track = album;
            }
        });
    }
}
