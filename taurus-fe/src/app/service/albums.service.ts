import { Injectable } from '@angular/core';
import { Albums, AlbumsCriteria } from '../module';
import { CommonOpenSearchService } from './common-open-search.service';

@Injectable({
    providedIn: 'root'
})
export class AlbumsService extends CommonOpenSearchService<Albums, AlbumsCriteria> {
    override resourceName(): string {
        return "albums";
    }
}
