import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { NotificationDeliveryAdmin, NotificationDeliveryFilters, NotificationDeliveryOrigin, NotificationDeliveryStatus, Page } from '../module';

@Injectable({ providedIn: 'root' })
export class NotificationDeliveryAdminService {
    private readonly resourceUrl = `${environment.baseUrl}/admin/notification-delivery`;

    constructor(private readonly http: HttpClient) {}

    getDeliveries(
        status: NotificationDeliveryStatus,
        page: number,
        size: number,
        sort = 'occurredAt,asc',
        filters: NotificationDeliveryFilters = {}
    ): Observable<Page<NotificationDeliveryAdmin>> {
        let params = new HttpParams().set('status', status).set('page', page).set('size', size).set('sort', sort);
        if (filters.origin) params = params.set('origin', filters.origin);
        if (filters.source) params = params.set('source', filters.source);
        if (filters.operation) params = params.set('operation', filters.operation);
        if (filters.from) params = params.set('from', filters.from);
        if (filters.to) params = params.set('to', filters.to);
        return this.http.get<Page<NotificationDeliveryAdmin>>(this.resourceUrl, { params });
    }

    retry(origin: NotificationDeliveryOrigin, id: number): Observable<NotificationDeliveryAdmin> {
        return this.http.post<NotificationDeliveryAdmin>(`${this.resourceUrl}/${origin}/${id}/retry`, {});
    }

    /** Chiusura tecnica motivata: ammessa solo sulle code push, non sul fan-out in-app. */
    close(origin: NotificationDeliveryOrigin, id: number, reason: string): Observable<NotificationDeliveryAdmin> {
        return this.http.post<NotificationDeliveryAdmin>(`${this.resourceUrl}/${origin}/${id}/close`, { reason });
    }

    retrySelected(refs: { origin: NotificationDeliveryOrigin; id: number }[]): Observable<{ retriedCount: number }> {
        return this.http.post<{ retriedCount: number }>(`${this.resourceUrl}/retry`, { refs });
    }
}
