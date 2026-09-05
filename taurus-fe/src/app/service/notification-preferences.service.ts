import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { NotificationPreferences } from '../module';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class NotificationPreferencesService {
    private readonly resourceUrl = `${environment.baseUrl}/notification-preferences`;

    constructor(private readonly http: HttpClient) {}

    getPreferences(): Observable<NotificationPreferences> {
        return this.http.get<NotificationPreferences>(this.resourceUrl);
    }

    savePreferences(preferences: NotificationPreferences): Observable<NotificationPreferences> {
        return this.http.put<NotificationPreferences>(this.resourceUrl, preferences);
    }
}
