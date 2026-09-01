import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { CalendarEventSeries, CalendarEventSeriesPreview, CalendarEventSeriesRequest } from '../module';

@Injectable({ providedIn: 'root' })
export class CalendarEventSeriesService {
    private readonly url = `${environment.baseUrl}/calendar-event-series`;

    constructor(private readonly http: HttpClient) {}

    preview(request: CalendarEventSeriesRequest): Observable<CalendarEventSeriesPreview> {
        return this.http.post<CalendarEventSeriesPreview>(`${this.url}/preview`, request);
    }

    create(request: CalendarEventSeriesRequest): Observable<CalendarEventSeries> {
        return this.http.post<CalendarEventSeries>(this.url, request);
    }

    get(id: number): Observable<CalendarEventSeries> {
        return this.http.get<CalendarEventSeries>(`${this.url}/${id}`);
    }

    update(id: number, request: CalendarEventSeriesRequest): Observable<CalendarEventSeries> {
        return this.http.patch<CalendarEventSeries>(`${this.url}/${id}`, request);
    }

    deleteFuture(id: number): Observable<CalendarEventSeries> {
        return this.http.delete<CalendarEventSeries>(`${this.url}/${id}`);
    }

    restoreOccurrence(seriesId: number, eventId: number): Observable<CalendarEventSeries> {
        return this.http.post<CalendarEventSeries>(`${this.url}/${seriesId}/occurrences/${eventId}/restore`, null);
    }
}
