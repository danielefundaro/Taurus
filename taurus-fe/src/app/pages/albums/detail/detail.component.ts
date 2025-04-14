import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Table } from 'primeng/table';
import { first } from 'rxjs';
import { IncludeTracksDialogComponent } from '../../../dialogs/include-tracks-dialog/include-tracks-dialog.component';
import { ImportsModule } from '../../../imports';
import { Albums, ChildrenEntities, Tracks } from '../../../module';
import { AlbumsService } from '../../../service';

@Component({
    selector: 'app-album-detail',
    imports: [
        ImportsModule,
    ],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [
        AlbumsService,
        DialogService,
    ],
})
export class DetailComponent {
    public sortOptions!: SelectItem[];
    public totalRecords: number = 0;
    public album: Albums = new Albums();
    public cols: string[];
    public selectedTracks: ChildrenEntities[];

    constructor(private readonly albumsService: AlbumsService, private readonly dialogService: DialogService,
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
        this.selectedTracks = [];
    }

    public addNew(): void {
        const dynamicDialogRef: DynamicDialogRef = this.dialogService.open(IncludeTracksDialogComponent, {
            header: "Aggiungi traccia",
            closable: true,
            draggable: true,
            resizable: true,
            modal: true,
            width: '50vw',
            breakpoints: { '1199px': '75vw', '575px': '90vw' },
        });

        dynamicDialogRef.onClose.pipe(first()).subscribe((result: Tracks[]) => {
            if (result) {
                if (!this.album.tracks) {
                    this.album.tracks = [];
                }

                this.album.tracks.push(...result.map(track => {
                    const childrenEntities = new ChildrenEntities();
                    childrenEntities.index = track.id;
                    childrenEntities.name = track.name;

                    return childrenEntities;
                }));

                this.album.tracks.forEach((track, i) => track.order = i + 1);
            }
        });
    }

    public onGlobalFilter(table: Table<ChildrenEntities>, event: Event): void {
        table.filterGlobal((event.target as HTMLInputElement).value, 'contains');
    }

    public editTrack(track: ChildrenEntities): void {
        console.log(track);
    }

    public deleteTrack(selectedTrack: ChildrenEntities): void {
        this.album.tracks?.splice(this.album.tracks.findIndex(track => selectedTrack.index === track.index), 1);
    }

    private loadElement(id: string) {
        this.albumsService.getById(id).pipe(first()).subscribe({
            next: (album: Albums) => {
                this.album = album;
            }
        });
    }
}
