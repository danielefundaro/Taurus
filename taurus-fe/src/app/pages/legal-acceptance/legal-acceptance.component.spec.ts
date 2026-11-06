import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { LegalDocumentStatus, LegalStatus, PublicLegalDocument } from '../../module';
import { LegalService, ToastService } from '../../service';
import { LegalAcceptanceComponent } from './legal-acceptance.component';

describe('LegalAcceptanceComponent', () => {
    const document = (overrides: Partial<LegalDocumentStatus> = {}): LegalDocumentStatus => ({
        id: 1,
        documentType: 'TERMS',
        version: '1.3',
        title: 'Termini di utilizzo',
        url: null,
        contentSha256: 'a'.repeat(64),
        changeSummary: 'Nuova sezione sugli account condivisi.',
        action: 'ACCEPT',
        publishedAt: '2026-11-12T09:00:00Z',
        required: true,
        accepted: false,
        previousVersionAccepted: false,
        ...overrides
    });
    const published = (html: string): PublicLegalDocument => ({
        documentType: 'TERMS',
        version: '1.3',
        title: 'Termini di utilizzo',
        action: 'ACCEPT',
        publishedAt: '2026-11-12T09:00:00Z',
        active: true,
        html,
        versions: []
    });

    let fixture: ComponentFixture<LegalAcceptanceComponent>;
    let legal: jasmine.SpyObj<LegalService>;
    let router: jasmine.SpyObj<Router>;

    async function open(status: LegalStatus): Promise<HTMLElement> {
        legal.getStatus.and.returnValue(of(status));
        await TestBed.configureTestingModule({
            imports: [LegalAcceptanceComponent],
            providers: [
                provideNoopAnimations(),
                { provide: LegalService, useValue: legal },
                { provide: Router, useValue: router },
                { provide: ToastService, useValue: jasmine.createSpyObj<ToastService>('ToastService', ['success']) },
                { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({ returnUrl: '/inventory' }) } } }
            ]
        }).compileComponents();
        fixture = TestBed.createComponent(LegalAcceptanceComponent);
        fixture.detectChanges();
        return fixture.nativeElement as HTMLElement;
    }

    beforeEach(() => {
        legal = jasmine.createSpyObj<LegalService>('LegalService', ['getStatus', 'getPublicDocument', 'accept']);
        router = jasmine.createSpyObj<Router>('Router', ['navigateByUrl']);
        router.navigateByUrl.and.resolveTo(true);
    });

    it('shows the text of each document to confirm and its permanent address', async () => {
        legal.getPublicDocument.and.returnValue(of(published('<h2 id="1-accesso">1. Accesso</h2><p>Testo dei termini.</p>')));

        const page = await open({ compliant: false, documents: [document()] });

        expect(legal.getPublicDocument).toHaveBeenCalledWith('TERMS', '1.3', jasmine.anything());
        expect(page.querySelector('.text-box .legal-text')?.textContent).toContain('Testo dei termini.');
        const link = page.querySelector('.document-heading a') as HTMLAnchorElement;
        expect(link.getAttribute('href')).toBe('/legal/terms/1.3');
        expect(link.target).toBe('_blank');
        expect(link.textContent).toContain('Apri in una nuova scheda');
    });

    it('says what changes only to who had confirmed another version', async () => {
        legal.getPublicDocument.and.returnValue(of(published('<p>Testo</p>')));

        const first = await open({ compliant: false, documents: [document(), document({ id: 2, documentType: 'PRIVACY', title: 'Informativa privacy', previousVersionAccepted: true })] });

        const changes = first.querySelectorAll('.changes');
        expect(changes.length).toBe(1);
        expect(changes[0].textContent).toContain('Cosa cambia');
        expect(changes[0].textContent).toContain('Nuova sezione sugli account condivisi.');
    });

    it('keeps the link to the address of a document published before its text lived in Taurus', async () => {
        const page = await open({ compliant: false, documents: [document({ url: 'https://example.test/termini', contentSha256: null })] });

        expect(legal.getPublicDocument).not.toHaveBeenCalled();
        expect(page.querySelector('.text-box')).toBeNull();
        expect((page.querySelector('.document-heading a') as HTMLAnchorElement).href).toBe('https://example.test/termini');
    });

    it('lets the user try again when the text cannot be read, without blocking the confirmation', async () => {
        legal.getPublicDocument.and.returnValues(
            throwError(() => new HttpErrorResponse({ status: 0 })),
            of(published('<p>Testo ritrovato</p>'))
        );

        const page = await open({ compliant: false, documents: [document()] });
        expect(page.textContent).toContain('Testo non caricato');

        fixture.componentInstance.loadText(document());
        fixture.componentInstance.confirmations[1] = true;
        fixture.detectChanges();

        expect(page.querySelector('.text-box')?.textContent).toContain('Testo ritrovato');
        expect(fixture.componentInstance.canSubmit).toBeTrue();
    });

    it('records the confirmations and returns to the page the user asked for', async () => {
        legal.getPublicDocument.and.returnValue(of(published('<p>Testo</p>')));
        legal.accept.and.returnValue(of({ compliant: true, documents: [document({ accepted: true })] }));
        await open({ compliant: false, documents: [document()] });

        fixture.componentInstance.confirmations[1] = true;
        fixture.componentInstance.submit();

        expect(legal.accept).toHaveBeenCalledWith([1]);
        expect(router.navigateByUrl).toHaveBeenCalledWith('/inventory');
    });

    it('leaves the page at once when nothing is left to confirm', async () => {
        await open({ compliant: true, documents: [document({ accepted: true })] });

        expect(router.navigateByUrl).toHaveBeenCalledWith('/inventory');
        expect(legal.getPublicDocument).not.toHaveBeenCalled();
    });
});
