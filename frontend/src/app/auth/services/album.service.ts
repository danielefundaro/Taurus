import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Page, Album, QueryPagination } from '../models';
import { CommonService } from './common.service';

@Injectable({
    providedIn: 'root'
})
export class AlbumService extends CommonService<Album> {
    baseApi(): string {
        return "albums";
    }
    searches(filter?: string | undefined, index?: number | undefined, size?: number | undefined, sort?: string | undefined, direction?: string | undefined): Observable<Page<Album>> {
        let q = undefined, sortDirection = undefined;

        if (filter) {
            q = `name:*${filter}* or date:*${filter}* or description:*${filter}*`;
        }

        if (sort && direction) {
            sortDirection = [`${direction == 'desc' ? '-' : '+'}${sort}`];
        }

        return this.searchesQuery(new QueryPagination(q, index, size, sortDirection));
    }
}
