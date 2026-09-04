import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { NotificationDeliveryAdmin, NotificationDeliveryStatus, Page } from '../module';

@Injectable({ providedIn: 'root' })
export class NotificationDeliveryAdminService {
    constructor(private readonly http: HttpClient) {}

    getDeliveries(status: NotificationDeliveryStatus, page: number, size: number, sort = 'occurredAt,asc'): Observable<Page<NotificationDeliveryAdmin>> {
        const params = new HttpParams().set('status', status).set('page', page).set('size', size).set('sort', sort);
        return this.http.get<Page<NotificationDeliveryAdmin>>(`${environment.baseUrl}/admin/notification-delivery`, { params });
    }

    retry(id: number): Observable<NotificationDeliveryAdmin> {
        return this.http.post<NotificationDeliveryAdmin>(`${environment.baseUrl}/admin/notification-delivery/${id}/retry`, {});
    }

    retrySelected(ids: number[]): Observable<{ retriedCount: number }> {
        return this.http.post<{ retriedCount: number }>(`${environment.baseUrl}/admin/notification-delivery/retry`, { ids });
    }
}
