import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { CalendarFeedService } from './calendar-feed.service';

describe('CalendarFeedService', () => {
    let service: CalendarFeedService;
    let http: HttpTestingController;
    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(CalendarFeedService);
        http = TestBed.inject(HttpTestingController);
    });
    afterEach(() => http.verify());

    it('keeps personal and administrative endpoints separate', () => {
        service.list().subscribe();
        const personal = http.expectOne(`${environment.baseUrl}/calendar-feeds`);
        expect(personal.request.method).toBe('GET');
        personal.flush([]);

        service.list(true).subscribe();
        const administrative = http.expectOne(`${environment.baseUrl}/admin/calendar-feeds`);
        expect(administrative.request.method).toBe('GET');
        administrative.flush([]);
    });

    it('uses lifecycle endpoints without placing the secret in query parameters', () => {
        const request = { name: 'Feed', detailLevel: 'MINIMAL' as const, pastDays: 90, futureMonths: 18, idempotencyKey: crypto.randomUUID() };
        service.create(request).subscribe();
        const create = http.expectOne(`${environment.baseUrl}/calendar-feeds`);
        expect(create.request.method).toBe('POST');
        expect(create.request.body).toEqual(request);
        create.flush({});
        const rotationKey = crypto.randomUUID();
        service.rotate('feed-id', false, rotationKey).subscribe();
        const rotate = http.expectOne(`${environment.baseUrl}/calendar-feeds/feed-id/rotate`);
        expect(rotate.request.method).toBe('POST');
        expect(rotate.request.body.idempotencyKey).toBe(rotationKey);
        rotate.flush({});
        service.revoke('feed-id').subscribe();
        const revoke = http.expectOne(`${environment.baseUrl}/calendar-feeds/feed-id`);
        expect(revoke.request.method).toBe('DELETE');
        revoke.flush(null);
        service.remove('feed-id', true).subscribe();
        const remove = http.expectOne(`${environment.baseUrl}/admin/calendar-feeds/feed-id/record`);
        expect(remove.request.method).toBe('DELETE');
        remove.flush(null);
    });

    it('reads the history of a feed from the endpoint of the role in use', () => {
        service.history('feed-id').subscribe();
        const personal = http.expectOne(`${environment.baseUrl}/calendar-feeds/feed-id/audit`);
        expect(personal.request.method).toBe('GET');
        personal.flush([]);

        service.history('feed-id', true).subscribe();
        const administrative = http.expectOne(`${environment.baseUrl}/admin/calendar-feeds/feed-id/audit`);
        expect(administrative.request.method).toBe('GET');
        administrative.flush([]);
    });

    it('resolves the event of a feed link by calendar uid', () => {
        const uid = 'd1f4c0de-0000-4000-8000-000000000001';
        let resolved: number | undefined;
        service.eventByUid(uid).subscribe((event) => (resolved = event.id));

        const request = http.expectOne(`${environment.baseUrl}/calendar-feeds/events/${uid}`);
        expect(request.request.method).toBe('GET');
        expect(request.request.urlWithParams).not.toContain('?');
        request.flush({ id: 42 });

        expect(resolved).toBe(42);
    });
});
