import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class PushSubscriptionService {
    private readonly baseUrl = `${environment.baseUrl}/push-subscriptions`;

    constructor(private readonly http: HttpClient) {}

    subscribe(subscription: PushSubscription): Observable<void> {
        const json = subscription.toJSON();
        const dto = {
            endpoint: json.endpoint,
            p256dh: json.keys?.['p256dh'],
            auth: json.keys?.['auth'],
        };
        return this.http.post<void>(this.baseUrl, dto);
    }

    unsubscribe(endpoint: string): Observable<void> {
        return this.http.delete<void>(this.baseUrl, {
            params: new HttpParams().set('endpoint', endpoint),
        });
    }
}
