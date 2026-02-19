import { Component, OnInit } from '@angular/core';
import { RouterModule } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { DataViewLazyLoadEvent } from 'primeng/dataview';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { SelectChangeEvent } from 'primeng/select';
import { delay, first } from 'rxjs';
import { AddFilesDialogComponent } from '../../dialogs/add-files-dialog/add-files-dialog.component';
import { AddTracksDialogComponent } from '../../dialogs/add-tracks-dialog/add-tracks-dialog.component';
import { ImportsModule } from '../../imports';
import { Page, Tracks, TracksCriteria } from '../../module';
import { StringFilter } from '../../module/criteria/filter';
import { ToastService, TracksService } from '../../service';

@Component({
    selector: 'app-tracks',
    imports: [
        RouterModule,
        ImportsModule,
    ],
    templateUrl: './tracks.component.html',
    styleUrl: './tracks.component.scss',
    providers: [
        TracksService,
        DialogService,
    ]
})
export class TracksComponent implements OnInit {
    public sortOptions!: SelectItem[];
    public layout: 'list' | 'grid' = 'list';
    public options = ['list', 'grid'];
    public totalRecords: number = 0;
    public dataViewLazyLoadEvent: DataViewLazyLoadEvent = { first: 0, rows: 5, sortField: 'name.keyword', sortOrder: 1 };
    public tracks: Tracks[];

    constructor(
        private readonly tracksService: TracksService,
        private readonly toastService: ToastService,
        private readonly dialogService: DialogService,
    ) {
        this.tracks = [];
    }

    ngOnInit() {
        this.sortOptions = [
            { label: 'Name A-Z', value: 'name.keyword' },
            { label: 'Name Z-A', value: '!name.keyword' },
        ];
    }

    public onSortChange(event: SelectChangeEvent): void {
        let value = event.value;

        if (value.indexOf('!') === 0) {
            this.dataViewLazyLoadEvent.sortOrder = -1;
            this.dataViewLazyLoadEvent.sortField = value.substring(1, value.length);
        } else {
            this.dataViewLazyLoadEvent.sortOrder = 1;
            this.dataViewLazyLoadEvent.sortField = value;
        }
    }

    public onLazyLoad(event: DataViewLazyLoadEvent): void {
        this.dataViewLazyLoadEvent = event;
        this.loadElements();
    }

    protected onGlobalFilter(event: Event): void {
        this.loadElements((event.target as HTMLInputElement).value);
    }

    public initials(name?: string | null): string {
        return (name ?? '').split(" ").slice(0, 2).map(s => s[0]).join(" ").toUpperCase();
    }

    public addNewFile(): void {
        const dynamicDialogRef: DynamicDialogRef = this.dialogService.open(AddFilesDialogComponent, {
            header: "Aggiungi traccia",
            closable: true,
            draggable: true,
            resizable: true,
            modal: true,
            width: '50vw',
            breakpoints: { '1199px': '75vw', '575px': '90vw' },
        });

        dynamicDialogRef.onClose.pipe(first()).subscribe((result: Tracks) => {
            if (result) {
                this.tracksService.create(result).pipe(delay(1000), first()).subscribe({
                    next: (track: Tracks) => {
                        this.toastService.success("Successo", "Traccia aggiunta con successo");
                        this.loadElements();
                    }
                });
            }
        });
    }

    public addNew(): void {
        const dynamicDialogRef: DynamicDialogRef = this.dialogService.open(AddTracksDialogComponent, {
            header: "Aggiungi traccia",
            closable: true,
            draggable: true,
            resizable: true,
            modal: true,
            width: '50vw',
            breakpoints: { '1199px': '75vw', '575px': '90vw' },
        });

        dynamicDialogRef.onClose.pipe(first()).subscribe((result: Tracks) => {
            if (result) {
                this.tracksService.create(result).pipe(delay(1000), first()).subscribe({
                    next: (track: Tracks) => {
                        this.toastService.success("Successo", "Traccia aggiunta con successo");
                        this.loadElements();
                    }
                });
            }
        });
    }

    public deleteElement(track: Tracks): void {
        this.tracksService.delete(track.id).pipe(delay(1000), first()).subscribe({
            next: (value: any) => {
                this.toastService.success("Successo", "Traccia eliminata con successo");
                this.loadElements();
            }
        });
    }

    private loadElements(search?: string): void {
        const tracksCriteria: TracksCriteria = new TracksCriteria();
        tracksCriteria.page = this.dataViewLazyLoadEvent.first / this.dataViewLazyLoadEvent.rows;
        tracksCriteria.size = this.dataViewLazyLoadEvent.rows;
        tracksCriteria.sort = [`${this.dataViewLazyLoadEvent.sortField},${this.dataViewLazyLoadEvent.sortOrder > 0 ? "asc" : "desc"}`];

        if (search) {
            tracksCriteria.name = new StringFilter();
            tracksCriteria.name.contains = search;
        }

        this.tracksService.getAll(tracksCriteria).pipe(first()).subscribe({
            next: (value: Page<Tracks>) => {
                this.tracks = value.content;
                this.totalRecords = value.totalElements;
            }
        });
    }
}
