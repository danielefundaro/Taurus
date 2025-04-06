import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { DataViewLazyLoadEvent } from 'primeng/dataview';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { SelectChangeEvent } from 'primeng/select';
import { first } from 'rxjs';
import { AddAlbumsDialogComponent } from '../../dialog/add-albums-dialog/add-albums-dialog.component';
import { ImportsModule } from '../../imports';
import { Albums, Page, Tracks, TracksCriteria } from '../../module';
import { AlbumsService, TracksService } from '../../service';

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
    public tracks: Tracks[];

    private dynamicDialogRef?: DynamicDialogRef;

    constructor(private readonly tracksService: TracksService, public dialogService: DialogService) {
        this.tracks = [];
    }

    ngOnInit() {
        this.sortOptions = [
            { label: 'Name A-Z', value: 'name.keyword' },
            { label: 'Name Z-A', value: '!name.keyword' },
        ];
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
            header: "Aggiungi traccia",
            closable: true,
            draggable: true,
            resizable: true,
            width: '50vw',
            breakpoints: { '1199px': '75vw', '575px': '90vw' },
        });

        this.dynamicDialogRef.onClose.pipe(first()).subscribe((result: Albums) => {
            if (result) {
                this.tracksService.create(result).pipe(first()).subscribe({
                    next: (album: Albums) => {
                        this.loadElements();
                    }
                });
            }
        });
    }

    public deleteElement(track: Tracks) {
        this.tracksService.delete(track.id).pipe(first()).subscribe({
            next: (value: any) => {
                this.loadElements();
            }
        });
    }

    private loadElements() {
        const tracksCriteria: TracksCriteria = new TracksCriteria();
        tracksCriteria.page = this.dataViewLazyLoadEvent.first / this.dataViewLazyLoadEvent.rows;
        tracksCriteria.size = this.dataViewLazyLoadEvent.rows;
        tracksCriteria.sort = [`${this.dataViewLazyLoadEvent.sortField},${this.dataViewLazyLoadEvent.sortOrder > 0 ? "asc" : "desc"}`];

        this.tracksService.getAll(tracksCriteria).pipe(first()).subscribe({
            next: (value: Page<Albums>) => {
                this.tracks = value.content;
                this.totalRecords = value.totalElements;
            }
        });
    }
}
