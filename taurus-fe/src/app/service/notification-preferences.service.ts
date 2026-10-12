import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { NotificationPreferences } from '../module';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class NotificationPreferencesService {
    private readonly resourceUrl = `${environment.baseUrl}/notification-preferences`;

    constructor(private readonly http: HttpClient) { }

    getPreferences(context?: HttpContext): Observable<NotificationPreferences> {
        return this.http.get<NotificationPreferences>(this.resourceUrl, { context });
    }

    savePreferences(preferences: NotificationPreferences, context?: HttpContext): Observable<NotificationPreferences> {
        return this.http.put<NotificationPreferences>(this.resourceUrl, preferences, { context });
    }
}
