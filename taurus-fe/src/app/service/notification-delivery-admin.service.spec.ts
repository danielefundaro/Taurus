import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { NotificationDeliveryAdminService } from './notification-delivery-admin.service';

describe('NotificationDeliveryAdminService', () => {
    let service: NotificationDeliveryAdminService;
    let http: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(NotificationDeliveryAdminService);
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    it('omits every filter that is not set', () => {
        service.getDeliveries('FAILED', 0, 12).subscribe();

        const request = http.expectOne((candidate) => candidate.url === `${environment.baseUrl}/admin/notification-delivery`);
        expect(request.request.params.get('status')).toBe('FAILED');
        expect(request.request.params.get('sort')).toBe('occurredAt,asc');
        expect(request.request.params.has('origin')).toBeFalse();
        expect(request.request.params.has('source')).toBeFalse();
        expect(request.request.params.has('operation')).toBeFalse();
        expect(request.request.params.has('from')).toBeFalse();
        request.flush({ content: [], totalElements: 0 });
    });

    it('sends origin, category, operation and range when they are set', () => {
        service
            .getDeliveries('FAILED', 1, 24, 'attempts,desc', {
                origin: 'PUSH',
                source: 'CALENDAR',
                operation: 'CREATE',
                from: '2026-09-01T00:00:00.000Z',
                to: '2026-09-02T00:00:00.000Z'
            })
            .subscribe();

        const request = http.expectOne((candidate) => candidate.url === `${environment.baseUrl}/admin/notification-delivery`);
        expect(request.request.params.get('origin')).toBe('PUSH');
        expect(request.request.params.get('source')).toBe('CALENDAR');
        expect(request.request.params.get('operation')).toBe('CREATE');
        expect(request.request.params.get('from')).toBe('2026-09-01T00:00:00.000Z');
        expect(request.request.params.get('to')).toBe('2026-09-02T00:00:00.000Z');
        expect(request.request.params.get('page')).toBe('1');
        request.flush({ content: [], totalElements: 0 });
    });

    it('addresses a retry by origin and id, because ids collide between origins', () => {
        service.retry('REMINDER', 3).subscribe();

        const request = http.expectOne(`${environment.baseUrl}/admin/notification-delivery/REMINDER/3/retry`);
        expect(request.request.method).toBe('POST');
        request.flush({});
    });

    it('sends a technical reason when closing a job', () => {
        service.close('PUSH', 7, 'DEVICE_UNREACHABLE').subscribe();

        const request = http.expectOne(`${environment.baseUrl}/admin/notification-delivery/PUSH/7/close`);
        expect(request.request.body).toEqual({ reason: 'DEVICE_UNREACHABLE' });
        request.flush({});
    });

    it('sends origin-qualified references for a bulk retry', () => {
        service
            .retrySelected([
                { origin: 'OUTBOX', id: 10 },
                { origin: 'PUSH', id: 10 }
            ])
            .subscribe();

        const request = http.expectOne(`${environment.baseUrl}/admin/notification-delivery/retry`);
        expect(request.request.body).toEqual({
            refs: [
                { origin: 'OUTBOX', id: 10 },
                { origin: 'PUSH', id: 10 }
            ]
        });
        request.flush({ retriedCount: 2 });
    });
});
