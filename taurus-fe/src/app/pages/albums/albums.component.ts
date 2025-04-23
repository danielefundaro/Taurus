import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { DataViewLazyLoadEvent } from 'primeng/dataview';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { SelectChangeEvent } from 'primeng/select';
import { first, forkJoin, Subscription } from 'rxjs';
import { AddAlbumsDialogComponent } from '../../dialogs/add-albums-dialog/add-albums-dialog.component';
import { ImportsModule } from '../../imports';
import { Albums, AlbumsCriteria, ChildrenEntities, Page } from '../../module';
import { StringFilter } from '../../module/criteria/filter';
import { AlbumsService, MediaService, PrinterService, TracksService } from '../../service';

@Component({
    selector: 'app-albums',
    imports: [
        RouterModule,
        ImportsModule,
    ],
    templateUrl: './albums.component.html',
    styleUrl: './albums.component.scss',
    providers: [
        AlbumsService,
        TracksService,
        MediaService,
        DialogService
    ]
})
export class AlbumsComponent implements OnInit, OnDestroy {
    protected sortOptions!: SelectItem[];
    protected layout: 'list' | 'grid' = 'list';
    protected options = ['list', 'grid'];
    protected totalRecords: number = 0;
    protected dataViewLazyLoadEvent: DataViewLazyLoadEvent = { first: 0, rows: 5, sortField: 'name.keyword', sortOrder: 1 };
    protected albums: Albums[];

    private $subscription?: Subscription;

    constructor(private readonly albumsService: AlbumsService, private readonly tracksService: TracksService,
        private readonly mediaService: MediaService, private readonly printerService: PrinterService,
        private readonly dialogService: DialogService, private readonly router: Router) {
        this.albums = [];
    }

    ngOnInit() {
        this.sortOptions = [
            { label: 'Name A-Z', value: 'name.keyword' },
            { label: 'Name Z-A', value: '!name.keyword' },
        ];
    }

    ngOnDestroy(): void {
        if (this.$subscription) {
            this.$subscription.unsubscribe();
        }
    }

    protected onSortChange(event: SelectChangeEvent) {
        let value = event.value;

        if (value.indexOf('!') === 0) {
            this.dataViewLazyLoadEvent.sortOrder = -1;
            this.dataViewLazyLoadEvent.sortField = value.substring(1, value.length);
        } else {
            this.dataViewLazyLoadEvent.sortOrder = 1;
            this.dataViewLazyLoadEvent.sortField = value;
        }
    }

    protected onLazyLoad(event: DataViewLazyLoadEvent): void {
        this.dataViewLazyLoadEvent = event;
        this.loadElements();
    }

    protected onGlobalFilter(event: Event): void {
        this.loadElements((event.target as HTMLInputElement).value);
    }

    protected initials(name?: string | null): string {
        return (name ?? '').split(" ").slice(0, 2).map(s => s[0]).join(" ").toUpperCase();
    }

    protected addNew(): void {
        const dynamicDialogRef: DynamicDialogRef = this.dialogService.open(AddAlbumsDialogComponent, {
            header: "Aggiungi album",
            closable: true,
            draggable: true,
            resizable: true,
            modal: true,
            width: '50vw',
            breakpoints: { '1199px': '75vw', '575px': '90vw' },
        });

        dynamicDialogRef.onClose.pipe(first()).subscribe((result: Albums) => {
            if (result) {
                this.albumsService.create(result).pipe(first()).subscribe({
                    next: (album: Albums) => {
                        this.loadElements();
                    }
                });
            }
        });
    }

    protected deleteElement(albums: Albums): void {
        this.albumsService.delete(albums.id).pipe(first()).subscribe({
            next: (value: any) => {
                this.loadElements();
            }
        });
    }

    protected preview(album: Albums): void {
        let childrenEntities: ChildrenEntities[] = [];

        if (album.tracks!.length > 0) {
            childrenEntities = album.tracks!;
        }

        this.$subscription = forkJoin(childrenEntities.map(track => this.tracksService.getById(track.index))).subscribe(tracks => {
            this.printerService.push(...tracks.flatMap(track => track.scores!))
            this.router.navigate(["preview"]);
        });
    }

    private loadElements(search?: string): void {
        const albumsCriteria: AlbumsCriteria = new AlbumsCriteria();
        albumsCriteria.page = this.dataViewLazyLoadEvent.first / this.dataViewLazyLoadEvent.rows;
        albumsCriteria.size = this.dataViewLazyLoadEvent.rows;
        albumsCriteria.sort = [`${this.dataViewLazyLoadEvent.sortField},${this.dataViewLazyLoadEvent.sortOrder > 0 ? "asc" : "desc"}`];

        if (search) {
            albumsCriteria.name = new StringFilter();
            albumsCriteria.name.contains = search;
        }

        this.albumsService.getAll(albumsCriteria).pipe(first()).subscribe({
            next: (value: Page<Albums>) => {
                this.albums = value.content;
                this.totalRecords = value.totalElements;
            }
        });
    }
}
