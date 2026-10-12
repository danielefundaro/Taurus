import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { InventoryService } from './inventory.service';
import { UserInventoryService } from './user-inventory.service';

describe('Inventory loan sheet endpoints', () => {
    let inventoryService: InventoryService;
    let userInventoryService: UserInventoryService;
    let http: HttpTestingController;
    const admin = `${environment.baseUrl}/inventory`;
    const personal = `${environment.baseUrl}/user/inventory`;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        inventoryService = TestBed.inject(InventoryService);
        userInventoryService = TestBed.inject(UserInventoryService);
        http = TestBed.inject(HttpTestingController);
    });
    afterEach(() => http.verify());

    it('reads the expiration range of a fixed-term loan, for today or for a past delivery', () => {
        inventoryService.getLoanExpirationRange(42).subscribe();
        const today = http.expectOne(`${admin}/users/42/loan-expiration-range`);
        expect(today.request.method).toBe('GET');
        expect(today.request.params.has('deliveredOn')).toBeFalse();
        today.flush({ deliveredOn: '2026-10-09', minimum: '2027-10-09', maximum: '2028-10-09' });

        inventoryService.getLoanExpirationRange(42, '2026-01-31').subscribe();
        const past = http.expectOne((request) => request.url === `${admin}/users/42/loan-expiration-range`);
        expect(past.request.params.get('deliveredOn')).toBe('2026-01-31');
        past.flush({ deliveredOn: '2026-01-31', minimum: '2027-01-31', maximum: '2028-01-31', sheetNumber: '2026/002' });
    });

    it('issues sheets from the proposal of the member and downloads the PDF as a blob', () => {
        const request = { assignmentIds: [3, 4], issuedOn: '2026-09-28', holderRole: 'MEMBER' as const, place: 'Roma' };

        inventoryService.getLoanSheetProposal(42).subscribe();
        const proposal = http.expectOne(`${admin}/users/42/loan-sheets/proposal`);
        expect(proposal.request.method).toBe('GET');
        proposal.flush({});

        inventoryService.previewLoanSheet(42, request).subscribe();
        const preview = http.expectOne(`${admin}/users/42/loan-sheets/preview`);
        expect(preview.request.method).toBe('POST');
        expect(preview.request.responseType).toBe('blob');
        expect(preview.request.body).toEqual(request);
        preview.flush(new Blob());

        inventoryService.issueLoanSheet(42, request).subscribe();
        const issue = http.expectOne(`${admin}/users/42/loan-sheets`);
        expect(issue.request.method).toBe('POST');
        issue.flush({});

        inventoryService.downloadLoanSheet(7).subscribe();
        const pdf = http.expectOne(`${admin}/loan-sheets/7/pdf`);
        expect(pdf.request.responseType).toBe('blob');
        pdf.flush(new Blob());
    });

    it('uses dedicated endpoints for the life cycle of the sheet', () => {
        inventoryService.linkLoanSheetAssignment(7, 9).subscribe();
        http.expectOne({ method: 'POST', url: `${admin}/loan-sheets/7/assignments/9` }).flush({});

        inventoryService.closeLoanSheet(7, { outcome: 'CHARGES', chargeAmount: 45, chargeDueDate: '2026-10-28' }).subscribe();
        const close = http.expectOne({ method: 'POST', url: `${admin}/loan-sheets/7/close` });
        expect(close.request.body.chargeAmount).toBe(45);
        close.flush({});

        inventoryService.settleLoanSheet(7).subscribe();
        http.expectOne({ method: 'POST', url: `${admin}/loan-sheets/7/settle` }).flush({});

        inventoryService.cancelLoanSheet(7, 'Errore').subscribe();
        const cancel = http.expectOne({ method: 'POST', url: `${admin}/loan-sheets/7/cancel` });
        expect(cancel.request.body).toEqual({ reason: 'Errore' });
        cancel.flush({});

        inventoryService.renewAssignment(9, { newExpirationDate: '2029-09-28', authorizationReference: '12', authorizationDate: '2028-09-01' }).subscribe();
        http.expectOne({ method: 'POST', url: `${admin}/assignments/9/renewals` }).flush({});

        inventoryService.substituteAssignment(9, { itemId: 11, quantity: 1 }).subscribe();
        http.expectOne({ method: 'POST', url: `${admin}/assignments/9/substitution` }).flush({});
    });

    it('checks, previews and activates the template of the sheet', () => {
        const file = { fileName: 'modello.html', content: '<p>{{foglio.numero}}</p>' };

        inventoryService.getLoanTemplate().subscribe();
        http.expectOne({ method: 'GET', url: `${admin}/loan-template` }).flush({});

        const durations = { minMonths: 12, maxMonths: 24, renewalMaxMonths: 24, totalMaxMonths: 36 };
        inventoryService.updateLoanDurations(durations, 3).subscribe();
        const update = http.expectOne({ method: 'PUT', url: `${admin}/loan-template/durations` });
        expect(update.request.body).toEqual({ durations, entityVersion: 3 });
        update.flush({});

        inventoryService.checkLoanTemplate(file).subscribe();
        const check = http.expectOne({ method: 'POST', url: `${admin}/loan-template/check` });
        expect(check.request.body).toEqual(file);
        check.flush({ valid: true, issues: [] });

        inventoryService.previewLoanTemplate(file).subscribe();
        const preview = http.expectOne({ method: 'POST', url: `${admin}/loan-template/preview` });
        expect(preview.request.responseType).toBe('blob');
        preview.flush(new Blob());

        inventoryService.activateLoanTemplate(file).subscribe();
        const activate = http.expectOne({ method: 'POST', url: `${admin}/loan-template` });
        expect(activate.request.body).toEqual(file);
        activate.flush({});

        inventoryService.activateLoanTemplateVersion(3).subscribe();
        http.expectOne({ method: 'POST', url: `${admin}/loan-template/versions/3/activate` }).flush({});

        inventoryService.useDefaultLoanTemplate().subscribe();
        http.expectOne({ method: 'POST', url: `${admin}/loan-template/use-default` }).flush({});
    });

    it('downloads templates and their previews as blobs', () => {
        inventoryService.downloadDefaultLoanTemplate().subscribe();
        inventoryService.previewDefaultLoanTemplate().subscribe();
        inventoryService.downloadLoanTemplateVersion(3).subscribe();
        inventoryService.previewLoanTemplateVersion(3).subscribe();

        for (const url of ['default/file', 'default/preview', 'versions/3/file', 'versions/3/preview']) {
            const request = http.expectOne({ method: 'GET', url: `${admin}/loan-template/${url}` });
            expect(request.request.responseType).toBe('blob');
            request.flush(new Blob());
        }
    });

    it('lets members list and download only through the personal namespace', () => {
        userInventoryService.getLoanSheets().subscribe();
        http.expectOne({ method: 'GET', url: `${personal}/loan-sheets` }).flush([]);

        userInventoryService.downloadLoanSheet(7).subscribe();
        const pdf = http.expectOne({ method: 'GET', url: `${personal}/loan-sheets/7/pdf` });
        expect(pdf.request.responseType).toBe('blob');
        pdf.flush(new Blob());
    });
});
