import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { DataViewLazyLoadEvent } from 'primeng/dataview';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { SelectChangeEvent } from 'primeng/select';
import { delay, first } from 'rxjs';
import { RoleEnums } from '../../constants';
import { AddAlbumsDialogComponent } from '../../dialogs/add-albums-dialog/add-albums-dialog.component';
import { ImportsModule } from '../../imports';
import { Albums, AlbumsCriteria, Page } from '../../module';
import { StringFilter } from '../../module/criteria/filter';
import { AlbumsService, MediaService, PrinterService, ToastService } from '../../service';

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
        MediaService,
        DialogService,
    ],
    changeDetection: ChangeDetectionStrategy.Default,
})
export class AlbumsComponent implements OnInit {
    protected sortOptions!: SelectItem[];
    protected layout: 'list' | 'grid' = 'list';
    protected options = ['list', 'grid'];
    protected totalRecords: number = 0;
    protected dataViewLazyLoadEvent: DataViewLazyLoadEvent = { first: 0, rows: 5, sortField: 'name.keyword', sortOrder: 1 };
    protected albums: Albums[];
    protected readonly RolesEnum: typeof RoleEnums = RoleEnums;

    constructor(
        private readonly albumsService: AlbumsService,
        private readonly printerService: PrinterService,
        private readonly toastService: ToastService,
        private readonly dialogService: DialogService,
    ) {
        this.albums = [];
    }

    ngOnInit() {
        this.sortOptions = [
            { label: 'Name A-Z', value: 'name.keyword' },
            { label: 'Name Z-A', value: '!name.keyword' },
        ];
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
                this.albumsService.create(result).pipe(delay(1000), first()).subscribe({
                    next: (album: Albums) => {
                        this.toastService.success("Successo", "Album aggiunto con successo");
                        this.loadElements();
                    }
                });
            }
        });
    }

    protected deleteElement(albums: Albums): void {
        this.albumsService.delete(albums.id).pipe(delay(1000), first()).subscribe({
            next: (value: any) => {
                this.toastService.success("Successo", "Album eliminato con successo");
                this.loadElements();
            }
        });
    }

    protected preview(album: Albums): void {
        this.printerService.preview(album);
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
