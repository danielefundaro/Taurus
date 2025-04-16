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
    protected sortOptions!: SelectItem[];
    protected totalRecords: number = 0;
    protected album: Albums = new Albums();
    protected cols: string[];
    protected selectedTracks: ChildrenEntities[];

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

    protected save(): void {
        this.albumsService.update(this.album.id, this.album).pipe(first()).subscribe({
            next: (album: Albums) => {
                this.loadElement(album.id);
            }
        });
    }

    protected print(): void {
        window.print();
    }

    protected deleteSelectedTracks(): void {
        this.selectedTracks.forEach(selectedTrack => {
            this.deleteTrack(selectedTrack);
        });
        this.selectedTracks = [];
    }

    protected onRowReorder(): void {
        this.album.tracks?.forEach((track, i) => track.order = i + 1);
    }

    protected addNew(): void {
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
                this.album.tracks ??= [];

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

    protected onGlobalFilter(table: Table<ChildrenEntities>, event: Event): void {
        table.filterGlobal((event.target as HTMLInputElement).value, 'contains');
    }

    protected deleteTrack(selectedTrack: ChildrenEntities): void {
        this.album.tracks?.splice(this.album.tracks.findIndex(track => selectedTrack.index === track.index), 1);
        this.album.tracks?.forEach((track, i) => track.order = i + 1);
    }

    private loadElement(id: string): void {
        this.albumsService.getById(id).pipe(first()).subscribe({
            next: (album: Albums) => {
                this.album = album;
            }
        });
    }
}
