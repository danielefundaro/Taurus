import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { Table } from 'primeng/table';
import { first } from 'rxjs';
import { ImportsModule } from '../../../imports';
import { Albums, ChildrenEntities } from '../../../module';
import { AlbumsService, TracksService } from '../../../service';

@Component({
    selector: 'app-album-detail',
    imports: [
        ImportsModule,
    ],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [
        AlbumsService,
        TracksService
    ],
})
export class DetailComponent {
    public sortOptions!: SelectItem[];
    public totalRecords: number = 0;
    public album: Albums = new Albums();
    public cols: string[];
    public selectedTracks: ChildrenEntities[];

    constructor(private readonly albumsService: AlbumsService, private readonly tracksService: TracksService,
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
        this.albumsService.update(this.album.id, this.album).pipe(first()).subscribe({
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
        this.album.tracks?.push(new ChildrenEntities());
    }

    public onGlobalFilter(table: Table<ChildrenEntities>, event: Event): void {
        table.filterGlobal((event.target as HTMLInputElement).value, 'contains');
    }

    public editTrack(track: ChildrenEntities): void {
        console.log(track);
    }

    public deleteTrack(selectedTrack: ChildrenEntities): void {
        this.album.tracks?.slice(this.album.tracks.findIndex(track => selectedTrack.index === track.index), 1);
    }

    private loadElement(id: string) {
        this.albumsService.getById(id).pipe(first()).subscribe({
            next: (album: Albums) => {
                this.album = album;
            }
        });
    }
}
