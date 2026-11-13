import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { HANDLED_ERRORS, withHandledErrors } from './api-error-context';
import { PayoutService } from './payout.service';

describe('PayoutService', () => {
    const base = `${environment.baseUrl}/finance/payouts`;
    let service: PayoutService;
    let http: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(withXhr()), provideHttpClientTesting()] });
        service = TestBed.inject(PayoutService);
        http = TestBed.inject(HttpTestingController);
    });
    afterEach(() => http.verify());

    it('keeps the versions of the parameters on their own endpoints', () => {
        const request = { validFrom: '2026-01-01', associationSharePercent: 20, roundingStep: 5 };

        service.getSettings().subscribe();
        expect(http.expectOne(`${base}/settings`).request.method).toBe('GET');

        service.createSetting(request).subscribe();
        const create = http.expectOne(`${base}/settings`);
        expect(create.request.method).toBe('POST');
        expect(create.request.body).toEqual(request);

        service.updateSetting(4, request).subscribe();
        expect(http.expectOne(`${base}/settings/4`).request.method).toBe('PUT');

        service.deleteSetting(4).subscribe();
        expect(http.expectOne(`${base}/settings/4`).request.method).toBe('DELETE');
    });

    it('asks which settled events a date would recalculate, without the version being edited', () => {
        service.getAffectedEvents('2026-03-01').subscribe();
        const created = http.expectOne((candidate) => candidate.url === `${base}/settings/affected-events`);
        expect(created.request.params.get('validFrom')).toBe('2026-03-01');
        expect(created.request.params.has('settingId')).toBeFalse();

        service.getAffectedEvents('2026-03-01', 9).subscribe();
        const edited = http.expectOne((candidate) => candidate.url === `${base}/settings/affected-events`);
        expect(edited.request.params.get('settingId')).toBe('9');
    });

    it('sends the preview values only when they differ from the saved ones', () => {
        service.getEvent(70).subscribe();
        const saved = http.expectOne((candidate) => candidate.url === `${base}/events/70`);
        expect(saved.request.params.keys()).toEqual([]);

        service.getEvent(70, { sharePercent: 15, roundingStep: 0 }, withHandledErrors(['payout.setting.notConfigured'])).subscribe();
        const preview = http.expectOne((candidate) => candidate.url === `${base}/events/70`);
        expect(preview.request.params.get('sharePercent')).toBe('15');
        expect(preview.request.params.get('roundingStep')).toBe('0');
        expect(preview.request.context.get(HANDLED_ERRORS)).toEqual(['payout.setting.notConfigured']);
    });

    it('assigns and removes the factor or the fixed payment of a participant', () => {
        service.setAdjustment(70, 3, { fixedAmount: 180, reason: 'Ingaggio' }).subscribe();
        const set = http.expectOne(`${base}/events/70/adjustments/3`);
        expect(set.request.method).toBe('PUT');
        expect(set.request.body).toEqual({ fixedAmount: 180, reason: 'Ingaggio' });

        service.removeAdjustment(70, 3).subscribe();
        expect(http.expectOne(`${base}/events/70/adjustments/3`).request.method).toBe('DELETE');
    });

    it('reads the members in a period and the shares of one member by status', () => {
        service.getMembers({ from: '2026-01-01' }).subscribe();
        const members = http.expectOne((candidate) => candidate.url === `${base}/users`);
        expect(members.request.params.get('from')).toBe('2026-01-01');
        expect(members.request.params.has('to')).toBeFalse();

        service.getMember(3, { status: 'ALL' }).subscribe();
        const all = http.expectOne((candidate) => candidate.url === `${base}/users/3`);
        expect(all.request.params.has('status')).toBeFalse();

        service.getMember(3, { to: '2026-12-31', status: 'DUE' }).subscribe();
        const due = http.expectOne((candidate) => candidate.url === `${base}/users/3`);
        expect(due.request.params.get('status')).toBe('DUE');
        expect(due.request.params.get('to')).toBe('2026-12-31');
    });

    it('posts the settlement with its idempotency key', () => {
        const request = { accountId: 1, bookingDate: '2026-11-13', requestKey: 'key', lines: [{ eventId: 70, userId: 3, amount: 150 }] };

        service.settle(request).subscribe();

        const settle = http.expectOne(`${base}/settlements`);
        expect(settle.request.method).toBe('POST');
        expect(settle.request.body).toEqual(request);
    });
});
