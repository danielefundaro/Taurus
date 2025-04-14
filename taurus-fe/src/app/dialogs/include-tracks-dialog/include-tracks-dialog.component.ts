import { NgFor, NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { first } from 'rxjs';
import { Page, Tracks, TracksCriteria } from '../../module';
import { TracksService } from '../../service';

@Component({
    selector: 'app-include-tracks-dialog',
    imports: [
        NgIf,
        NgFor,
        TableModule,
        TagModule,
        ButtonModule,
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
    protected totalRecords: number = 0;

    constructor(private readonly dialogRef: DynamicDialogRef<IncludeTracksDialogComponent>,
        private readonly tracksService: TracksService) {
        this.tracks = [];
    }

    protected onLazyLoad(event: TableLazyLoadEvent) {
        this.tableLazyLoadEvent = event;
        this.loadElements();
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    protected save(): void {
        this.dialogRef.close(this.tracks);
    }

    private loadElements(): void {
        const tracksCriteria: TracksCriteria = new TracksCriteria();
        tracksCriteria.page = this.tableLazyLoadEvent.first! / this.tableLazyLoadEvent.rows!;
        tracksCriteria.size = this.tableLazyLoadEvent.rows!;
        tracksCriteria.sort = ['name.keyword,asc'];

        this.tracksService.getAll(tracksCriteria).pipe(first()).subscribe({
            next: (value: Page<Tracks>) => {
                this.tracks = value.content;
                this.totalRecords = value.totalElements;
            }
        });
    }
}
