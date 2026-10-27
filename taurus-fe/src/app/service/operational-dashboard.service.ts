import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { DashboardCalendarAttention, OperationalDashboard } from '../module';

@Injectable({ providedIn: 'root' })
export class OperationalDashboardService {
    constructor(private readonly http: HttpClient) { }

    getOperations(): Observable<OperationalDashboard> {
        return this.http.get<OperationalDashboard>(`${environment.baseUrl}/dashboard/operations`);
    }

    /** Identificativi degli eventi contati da una riga di preparazione, ordinati per scadenza. */
    getAttentionEvents(attention: DashboardCalendarAttention): Observable<number[]> {
        return this.http.get<number[]>(`${environment.baseUrl}/dashboard/operations/calendar-events`, { params: { attention } });
    }
}
