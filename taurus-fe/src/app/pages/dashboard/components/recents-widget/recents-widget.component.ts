import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { RouterModule } from '@angular/router';
import { DetailSectionComponent } from '../../../../components/detail-section/detail-section.component';
import { EmptyStateComponent } from '../../../../components/empty-state/empty-state.component';
import { ListRowComponent } from '../../../../components/list-row/list-row.component';
import { Tracks } from '../../../../module';

@Component({
    selector: 'app-recents-widget',
    imports: [RouterModule, DetailSectionComponent, EmptyStateComponent, ListRowComponent],
    templateUrl: './recents-widget.component.html',
    styleUrl: './recents-widget.component.scss',
    host: {
        class: 'col-span-12'
    },
    changeDetection: ChangeDetectionStrategy.Default
})
export class RecentsWidgetComponent {
    @Input() tracks: Tracks[] = [];

    protected get visibleTracks(): Tracks[] {
        return this.tracks.slice(0, 5);
    }

    protected visibleTypes(track: Tracks): string[] {
        return track.type?.slice(0, 2) ?? [];
    }
}
