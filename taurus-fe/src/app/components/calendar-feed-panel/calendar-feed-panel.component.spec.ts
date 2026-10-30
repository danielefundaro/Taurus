import { HttpContext } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { CalendarFeed, CalendarFeedAuditEntry, CalendarFeedSecret } from '../../module';
import { CalendarFeedService, ConfirmService, ToastService } from '../../service';
import { CalendarFeedPanelComponent } from './calendar-feed-panel.component';

describe('CalendarFeedPanelComponent', () => {
    it('shows the required-name error without sending an invalid request', () => {
        const { component, service } = setup();

        (component as any).create();

        expect((component as any).nameTouched).toBeTrue();
        expect(service.create).not.toHaveBeenCalled();
    });

    it('creates the feed and exposes the one-time link when the name is present', () => {
        const { component, service, toast } = setup();
        (component as any).draft.name = 'Calendario eventi';

        (component as any).create();

        expect(service.create).toHaveBeenCalled();
        expect((component as any).secret.subscriptionUrl).toContain('/calendar.ics');
        expect((component as any).nameTouched).toBeFalse();
        expect(toast.success).toHaveBeenCalledWith('Feed creato', jasmine.stringContaining('non potrai recuperarlo'));
    });

    it('keeps the one-time link out of the persisted state of the browser', () => {
        localStorage.clear();
        sessionStorage.clear();
        const { component } = setup();
        (component as any).draft.name = 'Calendario eventi';

        (component as any).create();

        const stored = [...entriesOf(localStorage), ...entriesOf(sessionStorage)].join(' ');
        expect(stored).not.toContain('secret-token');
        expect(stored).not.toContain('calendar.ics');
    });

    it('removes a revoked feed after destructive confirmation', () => {
        const { component, service, toast } = setup();
        const feed = { id: 'feed-id', status: 'REVOKED' };

        (component as any).remove(feed);

        expect(service.remove).toHaveBeenCalledWith('feed-id', false, jasmine.any(HttpContext));
        expect(service.list).toHaveBeenCalled();
        expect(toast.success).toHaveBeenCalledWith('Feed eliminato', jasmine.any(String));
    });

    it('asks to confirm a rotation and reuses the same key until it succeeds', () => {
        const { component, service, toast } = setup();
        service.rotate.and.returnValue(throwError(() => new Error('offline')));

        (component as any).rotate({ id: 'feed-id', status: 'ACTIVE' });
        const firstKey = service.rotate.calls.mostRecent().args[2];

        service.rotate.and.returnValue(of(secretFeed()));
        (component as any).rotate({ id: 'feed-id', status: 'ACTIVE' });

        // Lo stesso tentativo, la stessa chiave: un retry non produce due link validi.
        expect(service.rotate.calls.mostRecent().args[2]).toBe(firstKey);
        expect(toast.success).toHaveBeenCalledWith('Nuovo link generato', jasmine.stringContaining('Copialo subito'));
    });

    it('loads the history of a feed and closes it again', () => {
        const { component, service } = setup();

        (component as any).openHistory({ id: 'feed-id', name: 'Calendario' });

        expect(service.history).toHaveBeenCalledWith('feed-id', false, jasmine.any(HttpContext));
        expect((component as any).historyEntries.length).toBe(1);
        expect((component as any).actionLabel('ROTATED')).toBe('Nuovo link generato');
        expect((component as any).sourceLabel('TENANT_DISABLED')).toBe('Tenant disattivato');

        (component as any).closeHistory();
        expect((component as any).historyFeed).toBeUndefined();
    });
});

describe('CalendarFeedPanelComponent rendering', () => {
    let fixture: ComponentFixture<CalendarFeedPanelComponent>;
    let service: jasmine.SpyObj<CalendarFeedService>;

    beforeEach(async () => {
        service = spyService();
        service.list.and.returnValue(of([feed()]));
        await TestBed.configureTestingModule({
            imports: [CalendarFeedPanelComponent],
            providers: [
                provideNoopAnimations(),
                { provide: CalendarFeedService, useValue: service },
                { provide: ConfirmService, useValue: jasmine.createSpyObj('ConfirmService', ['confirmDestructive', 'confirmReversible']) },
                { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['success', 'error']) }
            ]
        }).compileComponents();
        fixture = TestBed.createComponent(CalendarFeedPanelComponent);
    });

    it('invites the owner to rotate a link that has been exposed', () => {
        fixture.detectChanges();

        const text = fixture.nativeElement.textContent;
        expect(text).toContain('Il link viene mostrato una sola volta');
        expect(text).toContain('genera subito un nuovo link');
    });

    it('names type, owner and creation only in the administrative list', () => {
        fixture.detectChanges();
        expect(fixture.nativeElement.textContent).not.toContain('Proprietario');

        fixture.componentRef.setInput('admin', true);
        fixture.detectChanges();

        const headers = [...fixture.nativeElement.querySelectorAll('th')].map((cell: HTMLElement) => cell.textContent?.trim());
        expect(headers).toContain('Tipo');
        expect(headers).toContain('Proprietario');
        expect(headers).toContain('Creato');
        expect(fixture.nativeElement.textContent).toContain('Mario Rossi');
        expect(fixture.nativeElement.textContent).toContain('admin-1');
    });

    it('shows the history of a feed in a region a screen reader can reach', () => {
        service.history.and.returnValue(of([auditEntry()]));
        fixture.detectChanges();

        (fixture.componentInstance as any).openHistory(feed());
        fixture.detectChanges();

        const region: HTMLElement = fixture.nativeElement.querySelector('[role="region"]');
        expect(region.getAttribute('aria-label')).toContain('Calendario del direttivo');
        expect(region.textContent).toContain('Nuovo link generato');
        expect(region.textContent).toContain('Amministratore');
    });
});

function entriesOf(storage: Storage): string[] {
    return Object.keys(storage).map((key) => `${key}=${storage.getItem(key)}`);
}

function secretFeed(): CalendarFeedSecret {
    return {
        id: 'feed-id',
        name: 'Calendario eventi',
        feedType: 'TENANT',
        visibilityScope: 'INTERNAL',
        detailLevel: 'MINIMAL',
        pastDays: 90,
        futureMonths: 18,
        subscriptionUrl: 'https://example.test/api/calendar-subscriptions/v1/secret-token/calendar.ics',
        tokenShownOnce: true,
        createdAt: '2026-09-07T10:00:00Z'
    };
}

function feed(): CalendarFeed {
    return {
        id: 'feed-id',
        name: 'Calendario del direttivo',
        feedType: 'TENANT',
        visibilityScope: 'INTERNAL',
        detailLevel: 'MINIMAL',
        pastDays: 90,
        futureMonths: 18,
        status: 'ACTIVE',
        tokenFingerprint: 'a1b2c3d4e5f6',
        ownerUserId: 7,
        ownerName: 'Mario Rossi',
        createdBy: 'admin-1',
        createdAt: '2026-09-07T10:00:00Z',
        lastAccessedAt: null
    };
}

function auditEntry(): CalendarFeedAuditEntry {
    return {
        action: 'ROTATED',
        source: 'ADMIN',
        tokenVersion: 2,
        tokenFingerprint: 'a1b2c3d4e5f6',
        actor: 'admin-1',
        reason: null,
        occurredAt: '2026-09-08T09:00:00Z'
    };
}

function spyService(): jasmine.SpyObj<CalendarFeedService> {
    const service = jasmine.createSpyObj<CalendarFeedService>('CalendarFeedService', ['list', 'create', 'rotate', 'revoke', 'remove', 'history', 'eventByUid']);
    service.list.and.returnValue(of([]));
    service.create.and.returnValue(of(secretFeed()));
    service.rotate.and.returnValue(of(secretFeed()));
    service.revoke.and.returnValue(of(undefined));
    service.remove.and.returnValue(of(undefined));
    service.history.and.returnValue(of([auditEntry()]));
    return service;
}

function setup() {
    const service = spyService();
    const confirm = {
        confirmDestructive: jasmine.createSpy().and.callFake((options: { accept: () => void }) => options.accept()),
        confirmReversible: jasmine.createSpy().and.callFake((options: { accept: () => void }) => options.accept())
    };
    const toast = {
        success: jasmine.createSpy(),
        error: jasmine.createSpy()
    };
    const component = new CalendarFeedPanelComponent(service as never, confirm as never, toast as never);
    return { component, service, toast };
}
