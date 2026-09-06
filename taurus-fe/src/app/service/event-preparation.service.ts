import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { EventPreparationConfiguration, EventPreparationMaterial, EventPreparationProgramEntry, EventPreparationView } from '../module';

@Injectable({ providedIn: 'root' })
export class EventPreparationService {
    private readonly baseUrl = `${environment.baseUrl}/calendar-events`;
    constructor(private readonly http: HttpClient) {}
    get(eventId: number): Observable<EventPreparationView> {
        return this.http.get<EventPreparationView>(`${this.baseUrl}/${eventId}/preparation`);
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
    confirmBudget(eventId: number): Observable<EventPreparationView> {
        return this.http.post<EventPreparationView>(`${this.baseUrl}/${eventId}/preparation/budget-confirmation`, {});
    }
    confirmPresence(eventId: number): Observable<EventPreparationView> {
        return this.http.post<EventPreparationView>(`${this.baseUrl}/${eventId}/preparation/presence-confirmation`, {});
    }
    confirmNoMovements(eventId: number): Observable<EventPreparationView> {
        return this.http.post<EventPreparationView>(`${this.baseUrl}/${eventId}/preparation/no-movements-confirmation`, {});
    }
}
