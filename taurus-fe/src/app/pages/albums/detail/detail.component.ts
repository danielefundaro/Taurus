import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Table } from 'primeng/table';
import { delay, finalize, first } from 'rxjs';
import { RoleEnums, StateEnums, StateLabel, StateLabelsMap } from '../../../constants';
import { IncludeTracksDialogComponent } from '../../../dialogs/include-tracks-dialog/include-tracks-dialog.component';
import { ImportsModule } from '../../../imports';
import { DetailPageBase } from '../../_shared/detail-page.base';
import { Albums, ChildrenEntities, Tracks } from '../../../module';
import { DateConverterPipe } from '../../../pipe';
import { AlbumsService, ConfirmService, KeycloakService, PrinterService, ToastService } from '../../../service';

@Component({
    selector: 'app-album-detail',
    imports: [ImportsModule],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [AlbumsService, DialogService]
})
export class DetailComponent extends DetailPageBase implements OnInit {
    protected sortOptions!: SelectItem[];
    protected totalRecords: number = 0;
    protected album: Albums = new Albums();
    protected cols: string[];
    protected selectedTracks: ChildrenEntities[];
    protected autoFilteredStatesLabels: StateLabel[];
    protected RolesEnum: typeof RoleEnums = RoleEnums;
    protected readonly StateEnum: typeof StateEnums = StateEnums;
    protected readonly previewTooltip = "Aggiungi almeno una traccia per abilitare l'anteprima";

    constructor(
        private readonly albumsService: AlbumsService,
        private readonly keycloakService: KeycloakService,
        private readonly printerService: PrinterService,
        private readonly toastService: ToastService,
        private readonly dialogService: DialogService,
        private readonly activatedRouteService: ActivatedRoute,
        private readonly router: Router,
        private readonly confirmService: ConfirmService,
        private readonly dateConverterPipe: DateConverterPipe
    ) {
        super();
        this.cols = ['Codice', 'Ordine', 'Nome'];
        this.selectedTracks = [];
        this.autoFilteredStatesLabels = StateLabelsMap;
    }

    ngOnInit() {
        this.activatedRouteService.params.pipe(first()).subscribe((params) => {
            this.loadElement(params['id']);
        });
    }

    protected confirmDelete(): void {
        this.confirmService.confirmDestructive({
            title: 'Elimina album',
            consequence: 'L’album verrà eliminato definitivamente.',
            actionLabel: 'Elimina',
            accept: () => {
                this.albumsService
                    .delete(this.album.id)
                    .pipe(first())
                    .subscribe({
                        next: () => {
                            this.isDirty = false;
                            this.toastService.success('Successo', 'Album eliminato');
                            this.router.navigate(['/albums']);
                        }
                    });
            }
        });
    }

    protected save(): void {
        this.saving = true;
        this.albumsService
            .update(this.album.id, this.album)
            .pipe(
                delay(1000),
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe({
                next: (album: Albums) => {
                    this.isDirty = false;
                    this.toastService.success('Successo', 'Album aggiornato con successo');
                    this.loadElement(album.id);
                }
            });
    }

    protected preview(): void {
        this.printerService.preview(this.album, this.selectedTracks);
    }

    protected filterStates(event: AutoCompleteCompleteEvent) {
        this.autoFilteredStatesLabels = StateLabelsMap.filter((state) => (state.name.toLowerCase().includes(event.query.toLowerCase()) ? state : null)).filter((state) => state !== null) as StateLabel[];
    }

    protected confirmDeleteSelectedTracks(): void {
        this.confirmService.confirmDestructive({
            title: 'Rimuovi tracce',
            consequence: 'Le tracce selezionate verranno rimosse dall’album.',
            actionLabel: 'Rimuovi',
            accept: () => this.deleteSelectedTracks()
        });
    }

    protected deleteSelectedTracks(): void {
        this.selectedTracks.forEach((selectedTrack) => {
            this.deleteTrack(selectedTrack);
        });
        this.selectedTracks = [];
    }

    protected onRowReorder(): void {
        this.album.tracks?.forEach((track, i) => (track.order = i + 1));
        this.isDirty = true;
    }

    protected addNew(): void {
        const dynamicDialogRef: DynamicDialogRef = this.dialogService.open(IncludeTracksDialogComponent, {
            header: 'Aggiungi traccia',
            closable: true,
            draggable: true,
            resizable: true,
            modal: true,
            width: '50vw',
            breakpoints: { '1199px': '75vw', '575px': '90vw' }
        });

        dynamicDialogRef.onClose.pipe(first()).subscribe((result: Tracks[]) => {
            if (result) {
                this.album.tracks ??= [];

                this.album.tracks.push(
                    ...result.map((track) => {
                        const childrenEntities = new ChildrenEntities();
                        childrenEntities.index = track.id;
                        childrenEntities.name = track.name;

                        return childrenEntities;
                    })
                );

                this.onRowReorder();
            }
        });
    }

    protected onGlobalFilter(table: Table<ChildrenEntities>, event: Event): void {
        table.filterGlobal((event.target as HTMLInputElement).value, 'contains');
    }

    protected canReorder(): boolean {
        return [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST].includes(this.keycloakService.currentUserRole);
    }

    protected confirmDeleteTrack(track: ChildrenEntities): void {
        this.confirmService.confirmDestructive({
            title: 'Rimuovi traccia',
            consequence: 'La traccia verrà rimossa dall’album.',
            actionLabel: 'Rimuovi',
            accept: () => this.deleteTrack(track)
        });
    }

    protected deleteTrack(selectedTrack: ChildrenEntities): void {
        this.album.tracks?.splice(
            this.album.tracks.findIndex((track) => selectedTrack.index === track.index),
            1
        );
        this.onRowReorder();
    }

    private loadElement(id: number | string): void {
        this.albumsService
            .getById(Number(id))
            .pipe(first())
            .subscribe({
                next: (album: Albums) => {
                    this.album = album;
                    this.album.date = this.dateConverterPipe.transform(this.album.date);
                    this.isDirty = false;
                }
            });
    }
}
