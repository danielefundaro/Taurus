import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { RoleEnums } from '../constants';
import { BulkAvailabilityResult, CalendarEvents, CalendarEventsCriteria, EventPresentUser } from '../module';
import { CommonOpenSearchService } from './common-open-search.service';
import { KeycloakService } from './keycloak.service';

@Injectable({
    providedIn: 'root'
})
export class CalendarEventsService extends CommonOpenSearchService<CalendarEvents, CalendarEventsCriteria> {

    constructor(
        protected override readonly http: HttpClient,
        private readonly keycloakService: KeycloakService,
    ) {
        super(http);
    }

    override resourceName(): string {
        if (this.keycloakService.currentUserRole === RoleEnums.USER_EXTERNAL) {
            return 'external/calendar-events';
        }
        if (this.keycloakService.currentUserRole === RoleEnums.USER) {
            return 'user/calendar-events';
        }
        return 'calendar-events';
    }

    public setAvailability(id: number, available: boolean): Observable<CalendarEvents> {
        return this.http.patch<CalendarEvents>(`${this.baseUrl}/${this.resourceName()}/${id}/availability`, null, { params: new HttpParams().set('available', available.toString()) });
    }

    public cancelAvailability(id: number): Observable<CalendarEvents> {
        return this.http.delete<CalendarEvents>(`${this.baseUrl}/${this.resourceName()}/${id}/availability`);
    }

    public setPresentUsers(id: number, presentUsers: EventPresentUser[]): Observable<CalendarEvents> {
        return this.http.put<CalendarEvents>(`${this.baseUrl}/calendar-events/${id}/presences`, presentUsers);
    }

    public setSeriesAvailability(seriesId: number, available: boolean): Observable<BulkAvailabilityResult> {
        return this.http.patch<BulkAvailabilityResult>(`${this.baseUrl}/${this.resourceName()}/series/${seriesId}/availability`, null, {
            params: new HttpParams().set('available', available.toString()),
        });
    }

    public cancelSeriesAvailability(seriesId: number): Observable<BulkAvailabilityResult> {
        return this.http.delete<BulkAvailabilityResult>(`${this.baseUrl}/${this.resourceName()}/series/${seriesId}/availability`);
    }
}
