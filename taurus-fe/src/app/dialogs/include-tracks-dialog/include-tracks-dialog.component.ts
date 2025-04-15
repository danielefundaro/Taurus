import { NgFor, NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { first } from 'rxjs';
import { Page, Tracks, TracksCriteria } from '../../module';
import { StringFilter } from '../../module/criteria/filter';
import { TracksService } from '../../service';

@Component({
    selector: 'app-include-tracks-dialog',
    imports: [
        NgIf,
        NgFor,
        TableModule,
        TagModule,
        ButtonModule,
        IconFieldModule,
        InputIconModule,
        InputTextModule,
    ],
    templateUrl: './include-tracks-dialog.component.html',
    styleUrl: './include-tracks-dialog.component.scss',
    providers: [
        TracksService,
    ],
    changeDetection: ChangeDetectionStrategy.Default,
})
export class IncludeTracksDialogComponent {

    protected tableLazyLoadEvent: TableLazyLoadEvent = { first: 0, rows: 5, sortField: 'name.keyword', sortOrder: 1 };
    protected tracks: Tracks[];
    protected selectedTracks: Tracks[];
    protected totalRecords: number = 0;
    private readonly search: { name?: string, type?: string, composer?: string };

    constructor(private readonly dialogRef: DynamicDialogRef<IncludeTracksDialogComponent>,
        private readonly tracksService: TracksService) {
        this.tracks = [];
        this.selectedTracks = [];
        this.search = {};
    }

    protected onLazyLoad(event: TableLazyLoadEvent) {
        this.tableLazyLoadEvent = event;
        this.loadElements();
    }

    protected onGlobalFilter(event: Event, field: 'name' | 'type' | 'composer'): void {
        this.search[field] = (event.target as HTMLInputElement).value;
        this.loadElements();
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    protected save(): void {
        this.dialogRef.close(this.selectedTracks);
    }

    private loadElements(): void {
        const tracksCriteria: TracksCriteria = new TracksCriteria();
        tracksCriteria.page = this.tableLazyLoadEvent.first! / this.tableLazyLoadEvent.rows!;
        tracksCriteria.size = this.tableLazyLoadEvent.rows!;
        tracksCriteria.sort = ['name.keyword,asc'];

        if (this.search.name) {
            tracksCriteria.name = new StringFilter();
            tracksCriteria.name.contains = this.search.name;
        }

        if (this.search.type) {
            tracksCriteria.type = new StringFilter();
            tracksCriteria.type.contains = this.search.type;
        }

        if (this.search.composer) {
            tracksCriteria.composer = new StringFilter();
            tracksCriteria.composer.contains = this.search.composer;
        }

        // Reset selection
        this.selectedTracks = [];

        this.tracksService.getAll(tracksCriteria).pipe(first()).subscribe({
            next: (value: Page<Tracks>) => {
                this.tracks = value.content;
                this.totalRecords = value.totalElements;
            }
        });
    }
}
