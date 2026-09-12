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
});
