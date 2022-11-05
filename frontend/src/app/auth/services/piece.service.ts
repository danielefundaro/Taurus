import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Page, Piece, PieceTypeEnum, QueryPagination } from '../models';
import { CommonService } from './common.service';

@Injectable({
    providedIn: 'root'
})
export class PieceService extends CommonService<Piece> {

    baseApi(): string {
        return "pieces";
    }

    searches(filter?: string | undefined, index?: number | undefined, size?: number | undefined, sort?: string | undefined, direction?: string | undefined): Observable<Page<Piece>> {
        let q = undefined, sortDirection = undefined;
        let type: PieceTypeEnum | string | undefined = Piece.convertType(filter);
        type = type ? ` or type:${type}` : '';

        if (filter) {
            q = `id![3 , 4 , 5, 6] and ( name:*${filter}*${type} or author:*${filter}* or arranger:*${filter}* or description:*${filter}* )`;
        }

        if (sort && direction) {
            sortDirection = [`${direction == 'desc' ? '-' : '+'}${sort}`];
        }

        return this.searchesQuery(new QueryPagination(q, index, size, sortDirection));
    }

    searchesExcludeIds(ids?: Array<number>, filter?: string | undefined, index?: number | undefined, size?: number | undefined, sort?: string | undefined, direction?: string | undefined): Observable<Page<Piece>> {
        let q = undefined, sortDirection = undefined;
        let type: PieceTypeEnum | string | undefined = Piece.convertType(filter);
        type = type ? ` or type:${type}` : '';

        if (ids && ids.length > 0) {
            q = `id!${ids.join(" and id!")}`;

            if (ids.length === 1) {
                q = `id!${ids}`;
            }
        }

        if (filter) {
            const qFilter = `name:*${filter}*${type} or author:*${filter}* or arranger:*${filter}* or description:*${filter}*`;
            q = q ? `${q} and ( ${qFilter} )` : qFilter;
        }

        if (sort && direction) {
            sortDirection = [`${direction == 'desc' ? '-' : '+'}${sort}`];
        }

        return this.searchesQuery(new QueryPagination(q, index, size, sortDirection));
    }
}
