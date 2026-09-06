import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { RoleEnums } from '../constants';
import { EventPreparationConfiguration, EventPreparationMaterial, EventPreparationProgramEntry, EventPreparationView } from '../module';

@Injectable({ providedIn: 'root' })
export class EventPreparationService {
    private readonly baseUrl = `${environment.baseUrl}/calendar-events`;
    constructor(private readonly http: HttpClient) {}
    get(eventId: number, role: RoleEnums = RoleEnums.ADMIN): Observable<EventPreparationView> {
        const url =
            role === RoleEnums.ARCHIVIST
                ? `${this.baseUrl}/${eventId}/preparation/catalogue`
                : role === RoleEnums.USER
                  ? `${environment.baseUrl}/user/calendar-events/${eventId}/preparation`
                  : role === RoleEnums.USER_EXTERNAL
                    ? `${environment.baseUrl}/external/calendar-events/${eventId}/preparation`
                    : role === RoleEnums.TREASURER
                      ? `${environment.baseUrl}/finance/events/${eventId}/preparation`
                      : `${this.baseUrl}/${eventId}/preparation`;
        return this.http.get<EventPreparationView>(url);
    }
    configure(eventId: number, configuration: EventPreparationConfiguration): Observable<EventPreparationView> {
        return this.http.put<EventPreparationView>(`${this.baseUrl}/${eventId}/preparation/configuration`, configuration);
    }
    replaceProgram(eventId: number, entries: Pick<EventPreparationProgramEntry, 'trackId' | 'plannedDurationSeconds' | 'notes'>[]): Observable<EventPreparationView> {
        return this.http.put<EventPreparationView>(`${this.baseUrl}/${eventId}/preparation/program`, { entries });
    }
    replaceMaterials(eventId: number, materials: { itemId: number; assignmentId?: number; requiredQuantity: number; notes?: string }[]): Observable<EventPreparationView> {
        return this.http.put<EventPreparationView>(`${this.baseUrl}/${eventId}/preparation/materials`, { materials });
    }
    confirmMaterial(eventId: number, materialId: number): Observable<EventPreparationView> {
        return this.http.post<EventPreparationView>(`${this.baseUrl}/${eventId}/preparation/materials/${materialId}/confirm`, {});
    }
    confirmBudget(eventId: number, role: RoleEnums = RoleEnums.ADMIN): Observable<EventPreparationView> {
        const url = role === RoleEnums.TREASURER ? `${environment.baseUrl}/finance/events/${eventId}/budget-confirmation` : `${this.baseUrl}/${eventId}/preparation/budget-confirmation`;
        return this.http.post<EventPreparationView>(url, {});
    }
    confirmPresence(eventId: number): Observable<EventPreparationView> {
        return this.http.post<EventPreparationView>(`${this.baseUrl}/${eventId}/preparation/presence-confirmation`, {});
    }
    confirmNoMovements(eventId: number, role: RoleEnums = RoleEnums.ADMIN): Observable<EventPreparationView> {
        const url = role === RoleEnums.TREASURER ? `${environment.baseUrl}/finance/events/${eventId}/no-movements-confirmation` : `${this.baseUrl}/${eventId}/preparation/no-movements-confirmation`;
        return this.http.post<EventPreparationView>(url, {});
    }
}
