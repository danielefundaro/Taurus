import { Injectable } from '@angular/core';
import { Tracks, TracksCriteria } from '../module';
import { CommonOpenSearchService } from './common-open-search.service';

@Injectable({
    providedIn: 'root'
})
export class TracksService extends CommonOpenSearchService<Tracks, TracksCriteria> {
    override resourceName(): string {
        return "tracks";
    }

    public stream(id?: string): string {
        if (id) {
            return `${this.baseUrl}/${this.resourceName()}/${id}/stream`;
        }

        return `${this.baseUrl}/${this.resourceName()}/stream`;
    }
}
