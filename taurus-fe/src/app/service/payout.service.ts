import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { PayoutAdjustmentRequest, PayoutEvent, PayoutMember, PayoutMemberStatus, PayoutMemberSummary, PayoutSettledEvent, PayoutSetting, PayoutSettingRequest, PayoutSettlement, PayoutSettlementRequest } from '../module';

/** Gli endpoint dei compensi ai presenti: il backend calcola tutto, il client non ricalcola alcun importo. */
@Injectable({ providedIn: 'root' })
export class PayoutService {
    private readonly baseUrl = `${environment.baseUrl}/finance/payouts`;

    constructor(private readonly http: HttpClient) {}

    getSettings(): Observable<PayoutSetting[]> {
        return this.http.get<PayoutSetting[]>(`${this.baseUrl}/settings`);
    }

    createSetting(request: PayoutSettingRequest, context?: HttpContext): Observable<PayoutSetting> {
        return this.http.post<PayoutSetting>(`${this.baseUrl}/settings`, request, { context });
    }

    updateSetting(id: number, request: PayoutSettingRequest, context?: HttpContext): Observable<PayoutSetting> {
        return this.http.put<PayoutSetting>(`${this.baseUrl}/settings/${id}`, request, { context });
    }

    deleteSetting(id: number, context?: HttpContext): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/settings/${id}`, { context });
    }

    /** Gli eventi già liquidati che una versione con questa decorrenza ricalcolerebbe. */
    getAffectedEvents(validFrom: string, settingId?: number): Observable<PayoutSettledEvent[]> {
        let params = new HttpParams().set('validFrom', validFrom);
        if (settingId) params = params.set('settingId', settingId);
        return this.http.get<PayoutSettledEvent[]>(`${this.baseUrl}/settings/affected-events`, { params });
    }

    /** La simulazione dell'evento; percentuale e passo di anteprima non vengono salvati. */
    getEvent(eventId: number, preview: { sharePercent?: number | null; roundingStep?: number | null } = {}, context?: HttpContext): Observable<PayoutEvent> {
        let params = new HttpParams();
        if (preview.sharePercent !== undefined && preview.sharePercent !== null) params = params.set('sharePercent', preview.sharePercent);
        if (preview.roundingStep !== undefined && preview.roundingStep !== null) params = params.set('roundingStep', preview.roundingStep);
        return this.http.get<PayoutEvent>(`${this.baseUrl}/events/${eventId}`, { params, context });
    }

    setAdjustment(eventId: number, userId: number, request: PayoutAdjustmentRequest, context?: HttpContext): Observable<void> {
        return this.http.put<void>(`${this.baseUrl}/events/${eventId}/adjustments/${userId}`, request, { context });
    }

    removeAdjustment(eventId: number, userId: number, context?: HttpContext): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/events/${eventId}/adjustments/${userId}`, { context });
    }

    getMembers(period: { from?: string; to?: string } = {}): Observable<PayoutMemberSummary[]> {
        let params = new HttpParams();
        if (period.from) params = params.set('from', period.from);
        if (period.to) params = params.set('to', period.to);
        return this.http.get<PayoutMemberSummary[]>(`${this.baseUrl}/users`, { params });
    }

    getMember(userId: number, filters: { from?: string; to?: string; status?: PayoutMemberStatus } = {}): Observable<PayoutMember> {
        let params = new HttpParams();
        if (filters.from) params = params.set('from', filters.from);
        if (filters.to) params = params.set('to', filters.to);
        if (filters.status && filters.status !== 'ALL') params = params.set('status', filters.status);
        return this.http.get<PayoutMember>(`${this.baseUrl}/users/${userId}`, { params });
    }

    settle(request: PayoutSettlementRequest, context?: HttpContext): Observable<PayoutSettlement> {
        return this.http.post<PayoutSettlement>(`${this.baseUrl}/settlements`, request, { context });
    }
}
