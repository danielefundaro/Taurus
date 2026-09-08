import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Media, MediaCriteria } from '../module';
import { CommonOpenSearchService } from './common-open-search.service';

@Injectable({
    providedIn: 'root'
})
export class MediaService extends CommonOpenSearchService<Media, MediaCriteria> {
    override resourceName(): string {
        return 'media';
    }

    public stream(id: number): string {
        return `${this.baseUrl}/${this.resourceName()}/${id}/stream`;
    }

    public streamImage(id: number): Observable<Blob> {
        return this.http.get(`${this.baseUrl}/${this.resourceName()}/${id}/stream`, { responseType: 'blob' });
    }
}
