import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { CalendarEvents, Page, Users, UsersCalendarEventsCriteria, UsersCriteria } from '../module';
import { CommonOpenSearchService } from './common-open-search.service';

export interface UsersMeDTO {
    name?: string;
    lastName?: string;
    email?: string;
}

@Injectable({
    providedIn: 'root'
})
export class UsersService extends CommonOpenSearchService<Users, UsersCriteria> {
    override resourceName(): string {
        return "users";
    }

    public getOwn(): Observable<Users> {
        return this.http.get<Users>(`${this.baseUrl}/${this.resourceName()}/me`);
    }

    public getOwnCalendarEvents(criteria?: UsersCalendarEventsCriteria): Observable<Page<CalendarEvents>> {
        const options = this.createRequestOption(criteria);
        return this.http.get<Page<CalendarEvents>>(`${this.baseUrl}/${this.resourceName()}/me/calendar-events`, { params: options, observe: 'body' });
    }

    public partialUpdateOwn(dto: UsersMeDTO): Observable<Users> {
        return this.http.patch<Users>(`${this.baseUrl}/${this.resourceName()}/me`, dto);
    }

    public deleteOwn(): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/${this.resourceName()}/me`);
    }

    public deleteOwnForGdpr(): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/${this.resourceName()}/me/gdpr`);
    }

    public deleteForGdpr(id: string): Observable<void> {
        return this.http.delete<void>(`${this.baseUrl}/${this.resourceName()}/${id}/gdpr`);
    }

    public sendSetupEmail(id: string): Observable<void> {
        return this.http.put<void>(`${this.baseUrl}/${this.resourceName()}/${id}/send-setup-email`, null);
    }

    public getUserCalendarEvents(id: string, criteria?: UsersCalendarEventsCriteria): Observable<Page<CalendarEvents>> {
        const options = this.createRequestOption(criteria);
        return this.http.get<Page<CalendarEvents>>(`${this.baseUrl}/${this.resourceName()}/${id}/calendar-events`, { params: options, observe: 'body' });
    }
}
