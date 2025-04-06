import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { DataViewLazyLoadEvent } from 'primeng/dataview';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { SelectChangeEvent } from 'primeng/select';
import { Subscription } from 'rxjs';
import { AddAlbumsDialogComponent } from '../../dialog/add-albums-dialog/add-albums-dialog.component';
import { ImportsModule } from '../../imports';
import { Albums, AlbumsCriteria, Page } from '../../module';
import { AlbumsService } from '../../service';

@Component({
    selector: 'app-tracks',
    imports: [
        RouterModule,
        ImportsModule,
    ],
    templateUrl: './tracks.component.html',
    styleUrl: './tracks.component.scss',
    providers: [AlbumsService, DialogService]
})
export class TracksComponent {
    public sortOptions!: SelectItem[];
    public layout: 'list' | 'grid' = 'list';
    public options = ['list', 'grid'];
    public totalRecords: number = 0;
    public dataViewLazyLoadEvent: DataViewLazyLoadEvent = { first: 0, rows: 5, sortField: 'name.keyword', sortOrder: 1 };
    public albums: Albums[];

    private albumsSubscription?: Subscription;
    private albumsDeleteSubscription?: Subscription;
    private dynamicDialogRef?: DynamicDialogRef;

    constructor(private readonly albumsService: AlbumsService, public dialogService: DialogService) {
        this.albums = [];
    }

    ngOnInit() {
        this.sortOptions = [
            { label: 'Name A-Z', value: 'name.keyword' },
            { label: 'Name Z-A', value: '!name.keyword' },
        ];
    }

    ngOnDestroy() {
        if (this.albumsSubscription) {
            this.albumsSubscription.unsubscribe();
        }

        if (this.albumsDeleteSubscription) {
            this.albumsDeleteSubscription.unsubscribe();
        }
    }

    public onSortChange(event: SelectChangeEvent) {
        let value = event.value;

        if (value.indexOf('!') === 0) {
            this.dataViewLazyLoadEvent.sortOrder = -1;
            this.dataViewLazyLoadEvent.sortField = value.substring(1, value.length);
        } else {
            this.dataViewLazyLoadEvent.sortOrder = 1;
            this.dataViewLazyLoadEvent.sortField = value;
        }
    }

    public onLazyLoad(event: DataViewLazyLoadEvent) {
        this.dataViewLazyLoadEvent = event;
        this.loadElements();
    }

    public initials(name?: string | null): string {
        return (name ?? '').split(" ").slice(0, 2).map(s => s[0]).join(" ").toUpperCase();
    }

    public addNew(): void {
        this.dynamicDialogRef = this.dialogService.open(AddAlbumsDialogComponent, {
            header: "Aggiungi album",
            closable: true,
            draggable: true,
            resizable: true,
            width: '50vw',
            breakpoints: { '1199px': '75vw', '575px': '90vw' },
        });

        this.dynamicDialogRef.onClose.subscribe((result: Albums) => {
            if (result) {
                this.albumsService.create(result).subscribe({
                    next: (album: Albums) => {
                        this.loadElements();
                    }
                });
            }
        })
    }

    public deleteElement(albums: Albums) {
        this.albumsDeleteSubscription = this.albumsService.delete(albums.id).subscribe({
            next: (value: any) => {
                this.loadElements();
            }
        });
    }

    private loadElements() {
        const albumsCriteria: AlbumsCriteria = new AlbumsCriteria();
        albumsCriteria.page = this.dataViewLazyLoadEvent.first / this.dataViewLazyLoadEvent.rows;
        albumsCriteria.size = this.dataViewLazyLoadEvent.rows;
        albumsCriteria.sort = [`${this.dataViewLazyLoadEvent.sortField},${this.dataViewLazyLoadEvent.sortOrder > 0 ? "asc" : "desc"}`];

        this.albumsSubscription = this.albumsService.getAll(albumsCriteria).subscribe({
            next: (value: Page<Albums>) => {
                this.albums = value.content;
                this.totalRecords = value.totalElements;
            }
        });
    }
}
