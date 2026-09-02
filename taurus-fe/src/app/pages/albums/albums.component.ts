import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { delay, finalize, first, forkJoin } from 'rxjs';
import { RoleEnums, StateEnums } from '../../constants';
import { AddAlbumsDialogComponent } from '../../dialogs/add-albums-dialog/add-albums-dialog.component';
import { ImportsModule } from '../../imports';
import { Albums, AlbumsCriteria, Page } from '../../module';
import { StringFilter } from '../../module/criteria/filter';
import { AlbumsService, ConfirmService, ListLayout, ListLayoutService, MediaService, PrinterService, ToastService } from '../../service';
import { ListPageBase } from '../_shared/list-page.base';

@Component({
    selector: 'app-albums',
    imports: [RouterModule, ImportsModule],
    templateUrl: './albums.component.html',
    styleUrl: './albums.component.scss',
    providers: [AlbumsService, MediaService, DialogService],
    changeDetection: ChangeDetectionStrategy.Default
})
export class AlbumsComponent extends ListPageBase implements OnInit {
    protected sortOptions!: SelectItem[];
    protected layout: ListLayout = 'list';
    protected albums: Albums[];
    protected selectedAlbums: Albums[] = [];
    protected readonly RolesEnum: typeof RoleEnums = RoleEnums;
    protected readonly StateEnum: typeof StateEnums = StateEnums;

    constructor(
        private readonly albumsService: AlbumsService,
        private readonly printerService: PrinterService,
        private readonly toastService: ToastService,
        private readonly dialogService: DialogService,
        private readonly confirmService: ConfirmService,
        private readonly listLayoutService: ListLayoutService
    ) {
        super();
        this.albums = [];
    }

    ngOnInit() {
        this.sortOptions = [
            { label: 'Nome A-Z', value: 'name' },
            { label: 'Nome Z-A', value: '!name' }
        ];
        this.listLayoutService.observe('albums', (value) => (this.layout = value));
    }

    protected onLayoutChange(value: ListLayout): void {
        this.layout = value;
        this.listLayoutService.set('albums', value);
    }

    protected addNew(): void {
        const dynamicDialogRef: DynamicDialogRef = this.dialogService.open(AddAlbumsDialogComponent, {
            header: 'Aggiungi album',
            closable: true,
            draggable: true,
            resizable: true,
            modal: true,
            width: '40rem',
            breakpoints: { '767px': 'calc(100vw - 2rem)' }
        });

        dynamicDialogRef.onClose.pipe(first()).subscribe((result: Albums) => {
            if (result) {
                this.albumsService
                    .create(result)
                    .pipe(delay(1000), first())
                    .subscribe({
                        next: (album: Albums) => {
                            this.toastService.success('Successo', 'Album aggiunto con successo');
                            this.loadElements(this.searchTerm);
                        }
                    });
            }
        });
    }

    protected deleteElement(albums: Albums): void {
        this.confirmService.confirmDestructive({
            title: 'Elimina album',
            consequence: 'L’album verrà eliminato definitivamente.',
            actionLabel: 'Elimina',
            accept: () => {
                this.albumsService
                    .delete(albums.id)
                    .pipe(delay(1000), first())
                    .subscribe({
                        next: (value: any) => {
                            this.toastService.success('Successo', 'Album eliminato con successo');
                            this.loadElements(this.searchTerm);
                        }
                    });
            }
        });
    }

    protected preview(album: Albums): void {
        this.printerService.preview(album);
    }

    protected isSelected(item: Albums): boolean {
        return this.selectedAlbums.some((s) => s.id === item.id);
    }

    protected isAllSelected(items: Albums[]): boolean {
        return items.length > 0 && items.every((item) => this.isSelected(item));
    }

    protected toggleSelection(item: Albums): void {
        if (this.isSelected(item)) {
            this.selectedAlbums = this.selectedAlbums.filter((s) => s.id !== item.id);
        } else {
            this.selectedAlbums = [...this.selectedAlbums, item];
        }
    }

    protected toggleSelectAll(items: Albums[]): void {
        if (this.isAllSelected(items)) {
            this.selectedAlbums = this.selectedAlbums.filter((s) => !items.some((item) => item.id === s.id));
        } else {
            const notYetSelected = items.filter((item) => !this.isSelected(item));
            this.selectedAlbums = [...this.selectedAlbums, ...notYetSelected];
        }
    }

    protected deleteSelectedElements(): void {
        const count = this.selectedAlbums.length;
        this.confirmService.confirmDestructive({
            title: 'Elimina album selezionati',
            consequence: `I ${count} album selezionati verranno eliminati definitivamente.`,
            actionLabel: 'Elimina',
            accept: () => {
                forkJoin(this.selectedAlbums.map((item) => this.albumsService.delete(item.id)))
                    .pipe(delay(1000), first())
                    .subscribe({
                        next: () => {
                            this.selectedAlbums = [];
                            this.toastService.success('Successo', `${count} album eliminati con successo`);
                            this.loadElements(this.searchTerm);
                        }
                    });
            }
        });
    }

    protected loadElements(search?: string): void {
        this.loading = true;
        this.selectedAlbums = [];
        const albumsCriteria: AlbumsCriteria = new AlbumsCriteria();
        albumsCriteria.page = this.pageIndex;
        albumsCriteria.size = this.pageSize;
        albumsCriteria.sort = this.sortCriteria;

        if (search) {
            albumsCriteria.name = new StringFilter();
            albumsCriteria.name.contains = search;
        }

        this.albumsService
            .getAll(albumsCriteria)
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe({
                next: (value: Page<Albums>) => {
                    this.albums = value.content;
                    this.totalRecords = value.totalElements;
                }
            });
    }
}
