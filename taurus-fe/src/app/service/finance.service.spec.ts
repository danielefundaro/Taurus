import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { FinanceService } from './finance.service';

describe('FinanceService', () => {
    let service: FinanceService;
    let http: HttpTestingController;
    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(FinanceService);
        http = TestBed.inject(HttpTestingController);
    });
    afterEach(() => http.verify());

    it('keeps archiving, reactivation and deletion of an account on distinct endpoints', () => {
        service.archiveAccount(7).subscribe();
        const archive = http.expectOne(`${environment.baseUrl}/finance/accounts/7/archive`);
        expect(archive.request.method).toBe('PATCH');
        archive.flush(null);

        service.restoreAccount(7).subscribe();
        const restore = http.expectOne(`${environment.baseUrl}/finance/accounts/7/restore`);
        expect(restore.request.method).toBe('PATCH');
        restore.flush({ id: 7, name: 'Conto 3', accountType: 'CASH', currency: 'EUR', active: true });

        service.deleteAccount(7).subscribe();
        const remove = http.expectOne(`${environment.baseUrl}/finance/accounts/7`);
        expect(remove.request.method).toBe('DELETE');
        remove.flush(null);
    });

    it('sends every movement filter as a query parameter', () => {
        service
            .getMovements({ page: 1, size: 50, year: 2026, nature: 'TRANSFER', counterparty: 'Rossi', reconciled: false, query: '' })
            .subscribe();

        const request = http.expectOne((candidate) => candidate.url === `${environment.baseUrl}/finance/movements`);
        expect(request.request.params.get('page')).toBe('1');
        expect(request.request.params.get('size')).toBe('50');
        expect(request.request.params.get('year')).toBe('2026');
        expect(request.request.params.get('nature')).toBe('TRANSFER');
        expect(request.request.params.get('counterparty')).toBe('Rossi');
        expect(request.request.params.get('reconciled')).toBe('false');
        // Una ricerca vuota non è un filtro: non deve restringere il risultato.
        expect(request.request.params.has('query')).toBeFalse();
        request.flush({ content: [], totalElements: 0 });
    });

    it('filters the events tab by period, economic status and text', () => {
        service.getEvents({ from: '2026-01-01', to: '2026-12-31', status: 'PARTIALLY_SETTLED', query: 'natale' }).subscribe();

        const request = http.expectOne((candidate) => candidate.url === `${environment.baseUrl}/finance/events`);
        expect(request.request.params.get('from')).toBe('2026-01-01');
        expect(request.request.params.get('to')).toBe('2026-12-31');
        expect(request.request.params.get('status')).toBe('PARTIALLY_SETTLED');
        expect(request.request.params.get('query')).toBe('natale');
        request.flush({ content: [], totalElements: 0 });
    });

    it('exports the cashbook with the same filters as the list', () => {
        service.exportCashbook('xlsx', { year: 2026, accountId: 10, nature: 'ORDINARY', counterparty: 'Rossi' }).subscribe();

        const request = http.expectOne((candidate) => candidate.url === `${environment.baseUrl}/finance/reports/cashbook`);
        expect(request.request.params.get('format')).toBe('xlsx');
        expect(request.request.params.get('year')).toBe('2026');
        expect(request.request.params.get('accountId')).toBe('10');
        expect(request.request.params.get('nature')).toBe('ORDINARY');
        expect(request.request.params.get('counterparty')).toBe('Rossi');
        request.flush(new Blob());
    });

    it('saves the chosen order of accounts and categories', () => {
        service.reorderAccounts([3, 1, 2]).subscribe();
        const accounts = http.expectOne(`${environment.baseUrl}/finance/accounts/order`);
        expect(accounts.request.method).toBe('PATCH');
        expect(accounts.request.body).toEqual({ ids: [3, 1, 2] });
        accounts.flush([]);

        service.reorderCategories([2, 1]).subscribe();
        const categories = http.expectOne(`${environment.baseUrl}/finance/categories/order`);
        expect(categories.request.method).toBe('PATCH');
        expect(categories.request.body).toEqual({ ids: [2, 1] });
        categories.flush([]);
    });

    it('reads the statement of a single event', () => {
        service.getEventStatement(70).subscribe();

        const request = http.expectOne(`${environment.baseUrl}/finance/events/70/statement`);
        expect(request.request.method).toBe('GET');
        request.flush({ summary: { eventId: 70 }, feeVariance: 0, costVariance: 0, resultVariance: 0 });
    });
});
