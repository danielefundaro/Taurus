import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Table } from 'primeng/table';
import { delay, first } from 'rxjs';
import { RoleEnums, StateEnums } from '../../../constants';
import { IncludeTracksDialogComponent } from '../../../dialogs/include-tracks-dialog/include-tracks-dialog.component';
import { ImportsModule } from '../../../imports';
import { Albums, ChildrenEntities, Tracks } from '../../../module';
import { DateConverterPipe, EnumConverterPipe } from '../../../pipe';
import { AlbumsService, KeycloakService, PrinterService, ToastService } from '../../../service';

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
export class DetailComponent implements OnInit {
    protected sortOptions!: SelectItem[];
    protected totalRecords: number = 0;
    protected album: Albums = new Albums();
    protected cols: string[];
    protected selectedTracks: ChildrenEntities[];
    protected autoFilteredStates: Array<StateEnums>;
    protected RolesEnum: typeof RoleEnums = RoleEnums;

    private readonly states: Array<StateEnums>;

    constructor(
        private readonly albumsService: AlbumsService,
        private readonly keycloakService: KeycloakService,
        private readonly printerService: PrinterService,
        private readonly toastService: ToastService,
        private readonly dialogService: DialogService,
        private readonly activatedRouteService: ActivatedRoute,
        private readonly dateConverterPipe: DateConverterPipe,
        private readonly enumConverterPipe: EnumConverterPipe<StateEnums>,
    ) {
        this.cols = ["Codice", "Ordine", "Nome"];
        this.selectedTracks = [];

        this.states = this.enumConverterPipe.transform(StateEnums as unknown as StateEnums);
        this.autoFilteredStates = this.states;
    }

    ngOnInit() {
        this.activatedRouteService.params.pipe(first()).subscribe(params => {
            this.loadElement(params['id']);
        });
    }

    protected save(): void {
        this.albumsService.update(this.album.id, this.album).pipe(delay(1000), first()).subscribe({
            next: (album: Albums) => {
                this.toastService.success("Successo", "Album aggiornato con successo");
                this.loadElement(album.id);
            }
        });
    }

    protected preview(): void {
        this.printerService.preview(this.album, this.selectedTracks);
    }

    protected filterStates(event: AutoCompleteCompleteEvent) {
        this.autoFilteredStates = this.states.filter(state => state?.toLowerCase()?.includes(event.query.toLowerCase()));
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

    protected canReorder(): boolean {
        return this.keycloakService.currentUserRoles.some(role => [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST].includes(role));
    }

    protected deleteTrack(selectedTrack: ChildrenEntities): void {
        this.album.tracks?.splice(this.album.tracks.findIndex(track => selectedTrack.index === track.index), 1);
        this.album.tracks?.forEach((track, i) => track.order = i + 1);
    }

    private loadElement(id: string): void {
        this.albumsService.getById(id).pipe(first()).subscribe({
            next: (album: Albums) => {
                this.album = album;
                this.album.date = this.dateConverterPipe.transform(this.album.date);
            }
        });
    }
}
