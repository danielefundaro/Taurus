import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { Observable } from 'rxjs';
import { Media, Page, QueryPagination } from '../models';
import { CommonService } from './common.service';

@Injectable({
    providedIn: 'root'
})
export class MediaService extends CommonService<Media> {

    constructor(protected override http: HttpClient, private sanitizer: DomSanitizer) {
        super(http);
    }

    public baseApi(): string {
        return "media";
    }

    public searches(filter?: string | undefined, index?: number | undefined, size?: number | undefined, sort?: string | undefined, direction?: string | undefined): Observable<Page<Media>> {
        let q = undefined, sortDirection = undefined;

        if (filter) {
            q = `name:*${filter}* or type:*${filter}* or contentType:*${filter}* or instrument.name:*${filter}* or instrument.description:*${filter}*`;
        }

        if (sort && direction) {
            sortDirection = [`${direction == 'desc' ? '-' : '+'}${sort}`];
        }

        return this.searchesQuery(new QueryPagination(q, index, size, sortDirection));
    }

    public saveFile(id: number, file: File): Observable<Media> {
        const formData = new FormData();

        formData.append('id', id.toString());
        formData.append('file', file, file.name);

        return this.http.post<Media>(`${this.getBaseUrl}/file`, formData);
    }

    public stream(id: number): Observable<ArrayBuffer> {
        return this.http.get(`${this.getBaseUrl}/${id}/stream`, { responseType: 'arraybuffer' });
    }

    public bunchSanitizer(streams: ArrayBuffer[], mediaList?: Media[]): Media[] | undefined {
        streams.forEach((data, i) => {
            const media = mediaList?.at(i);

            if (media) {
                const objectUrl = window.URL.createObjectURL(new Blob([data], { type: media.contentType }));
                media.preview = this.sanitizer.bypassSecurityTrustUrl(objectUrl);
            }
        });

        return mediaList;
    }
}
