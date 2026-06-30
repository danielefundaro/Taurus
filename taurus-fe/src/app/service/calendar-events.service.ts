import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { RoleEnums } from '../constants';
import { CalendarEvents, CalendarEventsCriteria } from '../module';
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
        if (this.keycloakService.currentUserRole === RoleEnums.USER) {
            return 'user/calendar-events';
        }
        return 'calendar-events';
    }

    public setAvailability(id: string, available: boolean): Observable<CalendarEvents> {
        return this.http.put<CalendarEvents>(
            `${this.baseUrl}/user/calendar-events/${id}/availability`,
            null,
            { params: new HttpParams().set('available', available.toString()) },
        );
    }
}
