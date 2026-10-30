import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { CalendarFeed, CalendarFeedAuditEntry, CalendarFeedCreate, CalendarFeedSecret } from '../module';

@Injectable({ providedIn: 'root' })
export class CalendarFeedService {
    constructor(private readonly http: HttpClient) {}
    list(admin = false, context?: HttpContext): Observable<CalendarFeed[]> {
        return this.http.get<CalendarFeed[]>(this.url(admin), { context });
    }
    create(request: CalendarFeedCreate, admin = false, context?: HttpContext): Observable<CalendarFeedSecret> {
        return this.http.post<CalendarFeedSecret>(this.url(admin), request, { context });
    }
    rotate(id: string, admin = false, idempotencyKey: string = crypto.randomUUID(), context?: HttpContext): Observable<CalendarFeedSecret> {
        return this.http.post<CalendarFeedSecret>(`${this.url(admin)}/${id}/rotate`, { idempotencyKey }, { context });
    }
    revoke(id: string, admin = false, context?: HttpContext): Observable<void> {
        return this.http.delete<void>(`${this.url(admin)}/${id}`, { context });
    }
    remove(id: string, admin = false, context?: HttpContext): Observable<void> {
        return this.http.delete<void>(`${this.url(admin)}/${id}/record`, { context });
    }
    history(id: string, admin = false, context?: HttpContext): Observable<CalendarFeedAuditEntry[]> {
        return this.http.get<CalendarFeedAuditEntry[]>(`${this.url(admin)}/${id}/audit`, { context });
    }
    /** The event behind a feed link: the ICS file carries the calendar UID, never the database id. */
    eventByUid(uid: string, context?: HttpContext): Observable<{ id: number }> {
        return this.http.get<{ id: number }>(`${environment.baseUrl}/calendar-feeds/events/${uid}`, { context });
    }
    private url(admin: boolean): string {
        return `${environment.baseUrl}${admin ? '/admin' : ''}/calendar-feeds`;
    }
}
