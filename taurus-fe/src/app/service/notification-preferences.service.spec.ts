import { HttpContext, HttpContextToken, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { NotificationPreferences } from '../module';
import { NotificationPreferencesService } from './notification-preferences.service';

const preferences = (): NotificationPreferences => ({
    version: 3,
    timeZone: 'Europe/Rome',
    eventRemindersEnabled: true,
    defaultCalendarReminderMinutes: 30,
    quietHours: { enabled: true, start: '22:00', end: '07:00' },
    pushPausedUntil: null,
    digestLocalTime: '08:00',
    pushPreview: 'PRIVATE',
    categories: [
        { source: 'CALENDAR', inAppEnabled: true, pushMode: 'IMMEDIATE' },
        { source: 'FINANCE', inAppEnabled: true, pushMode: 'DAILY_DIGEST' }
    ]
});

describe('NotificationPreferencesService', () => {
    const url = `${environment.baseUrl}/notification-preferences`;
    let service: NotificationPreferencesService;
    let http: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(NotificationPreferencesService);
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    it('reads the whole aggregate of the signed-in member', () => {
        const expected = preferences();
        let received: NotificationPreferences | undefined;
        service.getPreferences().subscribe((value) => (received = value));

        const request = http.expectOne(url);
        expect(request.request.method).toBe('GET');
        request.flush(expected);

        expect(received).toEqual(expected);
    });

    it('writes the aggregate with its version, so a stale tab is refused by the backend', () => {
        const body = preferences();
        service.savePreferences(body).subscribe();

        const request = http.expectOne(url);
        expect(request.request.method).toBe('PUT');
        expect(request.request.body.version).toBe(3);
        expect(request.request.body.categories.length).toBe(2);
        request.flush({ ...body, version: 4 });
    });

    // Il contesto porta il riepilogo d'errore della pagina: senza inoltro il socio non vedrebbe il messaggio.
    it('forwards the HTTP context of the caller on both calls', () => {
        const token = new HttpContextToken<string>(() => '');
        const context = new HttpContext().set(token, 'profilo');

        service.getPreferences(context).subscribe();
        const read = http.expectOne(url);
        expect(read.request.context.get(token)).toBe('profilo');
        read.flush(preferences());

        service.savePreferences(preferences(), context).subscribe();
        const write = http.expectOne(url);
        expect(write.request.context.get(token)).toBe('profilo');
        write.flush(preferences());
    });

    it('reports the version conflict of a concurrent change to the caller', () => {
        let status = 0;
        service.savePreferences(preferences()).subscribe({ error: (error) => (status = error.status) });

        http.expectOne(url).flush({ message: 'conflict' }, { status: 409, statusText: 'Conflict' });

        expect(status).toBe(409);
    });
});
