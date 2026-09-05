import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { CalendarFeed, CalendarFeedCreate, CalendarFeedSecret } from '../module';

@Injectable({ providedIn: 'root' })
export class CalendarFeedService {
    constructor(private readonly http: HttpClient) {}
    list(admin = false): Observable<CalendarFeed[]> { return this.http.get<CalendarFeed[]>(this.url(admin)); }
    create(request: CalendarFeedCreate, admin = false): Observable<CalendarFeedSecret> { return this.http.post<CalendarFeedSecret>(this.url(admin), request); }
    rotate(id: string, admin = false): Observable<CalendarFeedSecret> { return this.http.post<CalendarFeedSecret>(`${this.url(admin)}/${id}/rotate`, {}); }
    revoke(id: string, admin = false): Observable<void> { return this.http.delete<void>(`${this.url(admin)}/${id}`); }
    private url(admin: boolean): string { return `${environment.baseUrl}${admin ? '/admin' : ''}/calendar-feeds`; }
}
