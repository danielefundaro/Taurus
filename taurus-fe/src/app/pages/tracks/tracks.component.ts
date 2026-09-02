import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { delay, finalize, first, forkJoin } from 'rxjs';
import { RoleEnums, StateEnums } from '../../constants';
import { AddFilesDialogComponent } from '../../dialogs/add-files-dialog/add-files-dialog.component';
import { AddTracksDialogComponent } from '../../dialogs/add-tracks-dialog/add-tracks-dialog.component';
import { ImportsModule } from '../../imports';
import { Page, Tracks, TracksCriteria } from '../../module';
import { StringFilter } from '../../module/criteria/filter';
import { ConfirmService, ListLayout, ListLayoutService, PrinterService, ToastService, TracksService } from '../../service';
import { ListPageBase } from '../_shared/list-page.base';

@Component({
    selector: 'app-tracks',
    imports: [RouterModule, ImportsModule],
    templateUrl: './tracks.component.html',
    styleUrl: './tracks.component.scss',
    providers: [TracksService, DialogService]
})
export class TracksComponent extends ListPageBase implements OnInit {
    protected sortOptions!: SelectItem[];
    protected layout: ListLayout = 'list';
    protected tracks: Tracks[];
    protected selectedTracks: Tracks[] = [];
    protected readonly RolesEnum: typeof RoleEnums = RoleEnums;
    protected readonly StateEnum: typeof StateEnums = StateEnums;

    constructor(
        private readonly tracksService: TracksService,
        private readonly printerService: PrinterService,
        private readonly toastService: ToastService,
        private readonly dialogService: DialogService,
        private readonly confirmService: ConfirmService,
        private readonly listLayoutService: ListLayoutService
    ) {
        super();
        this.tracks = [];
    }

    ngOnInit() {
        this.sortOptions = [
            { label: 'Nome A-Z', value: 'name' },
            { label: 'Nome Z-A', value: '!name' }
        ];
        this.listLayoutService.observe('tracks', (value) => (this.layout = value));
    }

    protected onLayoutChange(value: ListLayout): void {
        this.layout = value;
        this.listLayoutService.set('tracks', value);
    }

    public addNewFile(): void {
        const dynamicDialogRef: DynamicDialogRef = this.dialogService.open(AddFilesDialogComponent, {
            header: 'Aggiungi traccia',
            closable: true,
            draggable: true,
            resizable: true,
            modal: true,
            width: '40rem',
            breakpoints: { '767px': 'calc(100vw - 2rem)' }
        });

        dynamicDialogRef.onClose.pipe(first()).subscribe((result: any) => {
            if (result) {
                this.toastService.success('Successo', 'Traccia aggiunta con successo');
                this.loadElements(this.searchTerm);
            }
        });
    }

    public addNew(): void {
        const dynamicDialogRef: DynamicDialogRef = this.dialogService.open(AddTracksDialogComponent, {
            header: 'Aggiungi traccia',
            closable: true,
            draggable: true,
            resizable: true,
            modal: true,
            width: '40rem',
            breakpoints: { '767px': 'calc(100vw - 2rem)' }
        });

        dynamicDialogRef.onClose.pipe(first()).subscribe((result: Tracks) => {
            if (result) {
                this.tracksService
                    .create(result)
                    .pipe(delay(1000), first())
                    .subscribe({
                        next: (track: Tracks) => {
                            this.toastService.success('Successo', 'Traccia aggiunta con successo');
                            this.loadElements(this.searchTerm);
                        }
                    });
            }
        });
    }

    public deleteElement(track: Tracks): void {
        this.confirmService.confirmDestructive({
            title: 'Elimina traccia',
            consequence: 'La traccia verrà eliminata definitivamente.',
            actionLabel: 'Elimina',
            accept: () => {
                this.tracksService
                    .delete(track.id)
                    .pipe(delay(1000), first())
                    .subscribe({
                        next: (value: any) => {
                            this.toastService.success('Successo', 'Traccia eliminata con successo');
                            this.loadElements(this.searchTerm);
                        }
                    });
            }
        });
    }

    protected visibleTypes(track: Tracks): string[] {
        return (track.type ?? []).slice(0, 3);
    }

    protected hiddenTypes(track: Tracks): number {
        return Math.max((track.type?.length ?? 0) - 3, 0);
    }

    public preview(track: Tracks): void {
        this.printerService.preview(track);
    }

    protected isSelected(item: Tracks): boolean {
        return this.selectedTracks.some((s) => s.id === item.id);
    }

    protected isAllSelected(items: Tracks[]): boolean {
        return items.length > 0 && items.every((item) => this.isSelected(item));
    }

    protected toggleSelection(item: Tracks): void {
        if (this.isSelected(item)) {
            this.selectedTracks = this.selectedTracks.filter((s) => s.id !== item.id);
        } else {
            this.selectedTracks = [...this.selectedTracks, item];
        }
    }

    protected toggleSelectAll(items: Tracks[]): void {
        if (this.isAllSelected(items)) {
            this.selectedTracks = this.selectedTracks.filter((s) => !items.some((item) => item.id === s.id));
        } else {
            const notYetSelected = items.filter((item) => !this.isSelected(item));
            this.selectedTracks = [...this.selectedTracks, ...notYetSelected];
        }
    }

    protected deleteSelectedElements(): void {
        const count = this.selectedTracks.length;
        this.confirmService.confirmDestructive({
            title: 'Elimina tracce selezionate',
            consequence: `Le ${count} tracce selezionate verranno eliminate definitivamente.`,
            actionLabel: 'Elimina',
            accept: () => {
                forkJoin(this.selectedTracks.map((item) => this.tracksService.delete(item.id)))
                    .pipe(delay(1000), first())
                    .subscribe({
                        next: () => {
                            this.selectedTracks = [];
                            this.toastService.success('Successo', `${count} tracce eliminate con successo`);
                            this.loadElements(this.searchTerm);
                        }
                    });
            }
        });
    }

    protected loadElements(search?: string): void {
        this.loading = true;
        this.selectedTracks = [];
        const tracksCriteria: TracksCriteria = new TracksCriteria();
        tracksCriteria.page = this.pageIndex;
        tracksCriteria.size = this.pageSize;
        tracksCriteria.sort = this.sortCriteria;

        if (search) {
            tracksCriteria.name = new StringFilter();
            tracksCriteria.name.contains = search;
        }

        this.tracksService
            .getAll(tracksCriteria)
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe({
                next: (value: Page<Tracks>) => {
                    this.tracks = value.content;
                    this.totalRecords = value.totalElements;
                }
            });
    }
}
