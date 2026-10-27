import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { DialogService } from 'primeng/dynamicdialog';
import { of } from 'rxjs';
import { CalendarEvents, CalendarEventsCriteria, Page, Users } from '../../module';
import {
    CalendarEventSeriesService,
    CalendarEventsService,
    ConfirmService,
    KeycloakService,
    ListLayout,
    ListLayoutService,
    OperationalDashboardService,
    ToastService,
    UsersService
} from '../../service';
import { CalendarEventsComponent } from './calendar-events.component';

interface CalendarState {
    attention?: string;
    attentionDetail: string;
    layout: ListLayout;
    events: CalendarEvents[];
    totalRecords: number;
    loading: boolean;
    dataViewLazyLoadEvent: { first?: number; rows?: number; sortField?: string; sortOrder?: number };
    loadElements(search?: string): void;
}

const page = (content: CalendarEvents[], totalElements = content.length): Page<CalendarEvents> => ({ content, totalElements }) as Page<CalendarEvents>;

describe('CalendarEventsComponent attention filters', () => {
    let getAll: jasmine.Spy;
    let getAttentionEvents: jasmine.Spy;
    let getOwn: jasmine.Spy;

    const create = (attention: string | null): CalendarState => {
        const component = new CalendarEventsComponent(
            { getAll } as unknown as CalendarEventsService,
            {} as CalendarEventSeriesService,
            {} as ToastService,
            {} as DialogService,
            {} as ConfirmService,
            { observe: (_key: string, callback: (value: ListLayout) => void) => callback('grid'), set: () => undefined } as unknown as ListLayoutService,
            { snapshot: { queryParamMap: convertToParamMap(attention ? { attention } : {}) } } as unknown as ActivatedRoute,
            { getOwn, getAll: () => of({ content: [] }) } as unknown as UsersService,
            { isAdmin: true } as unknown as KeycloakService,
            { getAttentionEvents } as unknown as OperationalDashboardService
        );
        component.ngOnInit();
        return component as unknown as CalendarState;
    };

    beforeEach(() => {
        getAll = jasmine.createSpy('getAll').and.returnValue(of(page([])));
        getAttentionEvents = jasmine.createSpy('getAttentionEvents').and.returnValue(of([]));
        getOwn = jasmine.createSpy('getOwn').and.returnValue(of({ id: 5 } as Users));
    });

    it('pages on the server the events counted by the preparation rows of the dashboard', () => {
        getAttentionEvents.and.returnValue(of([42, 7, 19]));
        getAll.and.returnValue(of(page([{ id: 42 } as CalendarEvents], 3)));
        const state = create('event-preparation');
        state.dataViewLazyLoadEvent = { first: 12, rows: 12, sortField: 'startDate', sortOrder: 1 };

        state.loadElements('prova');

        expect(getAttentionEvents).toHaveBeenCalledOnceWith('event-preparation');
        const criteria = getAll.calls.mostRecent().args[0] as CalendarEventsCriteria;
        expect(criteria.id?.in).toEqual([42, 7, 19]);
        expect(criteria.page).toBe(1);
        expect(criteria.size).toBe(12);
        expect(criteria.name?.contains).toBe('prova');
        // Gli eventi da chiudere sono già passati: nessun filtro di data si aggiunge alla selezione del server.
        expect(criteria.startDate).toBeUndefined();
        expect(state.totalRecords).toBe(3);
        expect(state.events.map((event) => event.id)).toEqual([42]);
    });

    it('reads the event identifiers once and reuses them while paging', () => {
        getAttentionEvents.and.returnValue(of([4]));
        const state = create('event-follow-up');

        state.loadElements();
        state.loadElements();

        expect(getAttentionEvents).toHaveBeenCalledOnceWith('event-follow-up');
        expect(getAll).toHaveBeenCalledTimes(2);
        expect(state.attentionDetail).toBe('Sono mostrati gli eventi conclusi con le attività successive ancora da completare.');
    });

    it('shows an empty list without asking for every event when nothing is counted', () => {
        const state = create('event-preparation');

        state.loadElements();

        // Un filtro id.in vuoto sarebbe ignorato dal server e restituirebbe l'intero calendario.
        expect(getAll).not.toHaveBeenCalled();
        expect(state.events).toEqual([]);
        expect(state.totalRecords).toBe(0);
        expect(state.loading).toBeFalse();
    });

    it('forces the list layout while a filter of the dashboard is applied', () => {
        expect(create('event-preparation').layout).toBe('list');
        expect(create(null).layout).toBe('grid');
    });

    it('ignores attention values that are not offered by the dashboard', () => {
        for (const value of ['toString', 'constructor', 'unknown']) {
            const state = create(value);
            expect(state.attention).withContext(value).toBeUndefined();
            expect(state.attentionDetail).toBe('');
        }
    });

    it('keeps filtering on the client the events the user has not answered yet', () => {
        getAll.and.returnValue(
            of(
                page([
                    { id: 1, state: 'COMPLETE', availableUsers: [{ index: 5 }] } as unknown as CalendarEvents,
                    { id: 2, state: 'PUBLIC', availableUsers: [], unavailableUsers: [] } as unknown as CalendarEvents,
                    { id: 3, state: 'DRAFT' } as unknown as CalendarEvents
                ])
            )
        );
        const state = create('my-missing-availability');

        state.loadElements();

        expect(getOwn).toHaveBeenCalledTimes(1);
        expect(getAttentionEvents).not.toHaveBeenCalled();
        const criteria = getAll.calls.mostRecent().args[0] as CalendarEventsCriteria;
        expect(criteria.startDate?.greaterThanOrEqual).toBeDefined();
        expect(state.events.map((event) => event.id)).toEqual([2]);
        expect(state.attentionDetail).toBe('Sono mostrati gli eventi futuri a cui devi ancora rispondere.');
    });
});
