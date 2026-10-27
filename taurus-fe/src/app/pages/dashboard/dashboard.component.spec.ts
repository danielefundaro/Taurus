import { fakeAsync, tick } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';
import { OperationalDashboard } from '../../module';
import {
    AlbumsService,
    CalendarEventsService,
    InventoryService,
    KeycloakService,
    LayoutService,
    NoticesService,
    NotificationCenterService,
    NotificationPreferencesService,
    OperationalDashboardService,
    TenantFeatureService,
    TenantsService,
    ToastService,
    TracksService,
    UserInventoryService,
    UsersService
} from '../../service';
import { DashboardComponent } from './dashboard.component';

interface DashboardState {
    operations?: OperationalDashboard;
    operationsInitialLoading: boolean;
    operationsError: boolean;
    operationsRefreshWarning: boolean;
    refreshOperations(): void;
    onVisibilityChange(): void;
}

const snapshot = (groupCount: number): OperationalDashboard => ({
    generatedAt: '2026-10-27T10:00:00+01:00',
    status: 'COMPLETE',
    summary: { groupCount, dangerCount: 0, warningCount: groupCount, infoCount: 0 },
    items: [],
    unavailableDomains: []
});

describe('DashboardComponent operations snapshot', () => {
    let tenant: string | undefined;
    let responses: Observable<OperationalDashboard>[];
    let getOperations: jasmine.Spy;
    let state: DashboardState;

    beforeEach(() => {
        tenant = 'banda-a';
        responses = [];
        getOperations = jasmine.createSpy('getOperations').and.callFake(() => responses.shift() ?? of(snapshot(0)));
        const keycloak = {
            get currentUserTenantCode() {
                return tenant;
            }
        } as unknown as KeycloakService;
        const component = new DashboardComponent(
            keycloak,
            {} as TenantsService,
            {} as UsersService,
            {} as AlbumsService,
            {} as TracksService,
            {} as CalendarEventsService,
            {} as InventoryService,
            {} as UserInventoryService,
            {} as NoticesService,
            {} as LayoutService,
            {} as NotificationCenterService,
            {} as NotificationPreferencesService,
            { getOperations } as unknown as OperationalDashboardService,
            { inventoryEnabled: () => false } as unknown as TenantFeatureService,
            {} as ToastService,
            {} as Router
        );
        state = component as unknown as DashboardState;
        spyOnProperty(document, 'visibilityState', 'get').and.returnValue('visible');
    });

    it('keeps the snapshot within five minutes while the tenant does not change', () => {
        responses.push(of(snapshot(2)));
        state.refreshOperations();

        state.onVisibilityChange();

        expect(getOperations).toHaveBeenCalledTimes(1);
        expect(state.operations?.summary.groupCount).toBe(2);
    });

    it('never shows the snapshot of the previous tenant while the new one loads', () => {
        responses.push(of(snapshot(2)));
        state.refreshOperations();
        const pending = new Subject<OperationalDashboard>();
        responses.push(pending);

        tenant = 'banda-b';
        state.onVisibilityChange();

        expect(getOperations).toHaveBeenCalledTimes(2);
        expect(state.operations).toBeUndefined();
        // Senza snapshot del tenant corrente il widget torna allo scheletro del primo caricamento.
        expect(state.operationsInitialLoading).toBeTrue();

        pending.next(snapshot(5));
        pending.complete();

        expect(state.operations?.summary.groupCount).toBe(5);
    });

    it('discards a response that arrives after the tenant changed and reads the new tenant again', fakeAsync(() => {
        const pending = new Subject<OperationalDashboard>();
        responses.push(pending, of(snapshot(7)));
        state.refreshOperations();

        tenant = 'banda-b';
        pending.next(snapshot(3));
        pending.complete();

        expect(state.operations).toBeUndefined();
        tick();

        expect(getOperations).toHaveBeenCalledTimes(2);
        expect(state.operations?.summary.groupCount).toBe(7);
    }));

    it('reports an error instead of a stale warning when the reading of the new tenant fails', () => {
        responses.push(of(snapshot(2)));
        state.refreshOperations();
        responses.push(throwError(() => new Error('offline')));

        tenant = 'banda-b';
        state.refreshOperations();

        expect(state.operations).toBeUndefined();
        expect(state.operationsError).toBeTrue();
        expect(state.operationsRefreshWarning).toBeFalse();
    });

    it('keeps the snapshot of the same tenant with a warning when a refresh fails', () => {
        responses.push(of(snapshot(2)), throwError(() => new Error('offline')));
        state.refreshOperations();

        state.refreshOperations();

        expect(state.operations?.summary.groupCount).toBe(2);
        expect(state.operationsRefreshWarning).toBeTrue();
        expect(state.operationsError).toBeFalse();
    });
});
