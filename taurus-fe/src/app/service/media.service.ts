import { Injectable } from '@angular/core';
import { Media, MediaCriteria } from '../module';
import { CommonOpenSearchService } from './common-open-search.service';

@Injectable({
    providedIn: 'root'
})
export class MediaService extends CommonOpenSearchService<Media, MediaCriteria> {
    override resourceName(): string {
        return "media";
    }
}
