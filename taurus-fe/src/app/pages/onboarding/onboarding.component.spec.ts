import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { OnboardingContext, OnboardingIssue, OnboardingJob, OnboardingOutcome, OnboardingRow } from '../../module';
import { OnboardingService, ToastService } from '../../service';
import { OnboardingComponent } from './onboarding.component';

describe('OnboardingComponent', () => {
    let fixture: ComponentFixture<OnboardingComponent>;
    let component: OnboardingComponent;
    let onboarding: jasmine.SpyObj<OnboardingService>;

    const context: OnboardingContext = {
        tenantCode: 'CODICE-NASCOSTO',
        tenantName: 'Banda test',
        schemaActive: true,
        users: 0,
        instruments: 0,
        inventoryItems: 0,
        financialAccounts: 0,
        supportedTemplateVersions: [1],
        availableSections: ['INSTRUMENTS', 'USERS', 'INVENTORY', 'CATEGORIES', 'ACCOUNTS', 'OPENING_BALANCES'],
        limits: { maxFileSizeBytes: 10485760, maxTotalRows: 1000, maxUserRows: 100 }
    };

    const readyJob: OnboardingJob = {
        id: 42,
        fileName: 'tenant.xlsx',
        format: 'XLSX',
        templateVersion: 1,
        status: 'READY',
        stage: 'VALIDATION_COMPLETED',
        progressPercentage: 100,
        counts: { total: 2, valid: 2, warnings: 0, errors: 0 },
        createdAt: '2026-09-07T10:00:00+02:00',
        setupEmailFailures: 0
    };

    const outcome: OnboardingOutcome = {
        status: 'COMPLETED',
        startedAt: '2026-09-07T10:00:00+02:00',
        completedAt: '2026-09-07T10:01:30+02:00',
        durationMillis: 90000,
        identitiesCreated: 3,
        identitiesLinked: 1,
        setupEmailFailures: 0,
        sections: [{ section: 'USERS', total: 4, valid: 4, warnings: 0, errors: 0, create: 3, reuse: 1, skip: 0, applied: 4 }]
    };

    /** Una pagina come quella del backend: la tabella pigra ricarica da sola, quindi i dati arrivano dal servizio. */
    const page = <T>(content: T[]) => ({
        content,
        pageable: { pageNumber: 0, pageSize: 50, sort: { empty: true, sorted: false, unsorted: true }, offset: 0, paged: true, unpaged: false },
        last: true,
        totalPages: content.length ? 1 : 0,
        totalElements: content.length,
        size: 50,
        number: 0,
        sort: { empty: true, sorted: false, unsorted: true },
        first: true,
        numberOfElements: content.length,
        empty: content.length === 0
    });

    beforeEach(async () => {
        const emptyPage = page([]);
        onboarding = jasmine.createSpyObj<OnboardingService>('OnboardingService', ['context', 'jobs', 'rows', 'issues', 'sections', 'outcome', 'job', 'upload', 'apply']);
        onboarding.context.and.returnValue(of(context));
        onboarding.jobs.and.returnValue(of(emptyPage));
        onboarding.rows.and.returnValue(of(emptyPage));
        onboarding.issues.and.returnValue(of(emptyPage));
        onboarding.sections.and.returnValue(of([]));
        onboarding.outcome.and.returnValue(of(outcome));
        onboarding.job.and.returnValue(of({ job: readyJob, etag: '"42-READY-VALIDATION_COMPLETED-100-2-0"' }));

        await TestBed.configureTestingModule({
            imports: [OnboardingComponent],
            providers: [
                { provide: OnboardingService, useValue: onboarding },
                { provide: ToastService, useValue: jasmine.createSpyObj<ToastService>('ToastService', ['error']) },
                provideRouter([]),
                { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => null } } } },
                provideNoopAnimations()
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(OnboardingComponent);
        component = fixture.componentInstance;
        component['job'] = readyJob;
        component['activeStep'] = 4;
        fixture.detectChanges();
    });

    it('returns to the confirmation after reviewing a ready import', () => {
        buttonWithText('Controlla').click();
        fixture.detectChanges();

        expect(component['activeStep']).toBe(3);

        buttonWithText('Vai alla conferma').click();
        fixture.detectChanges();

        expect(component['activeStep']).toBe(4);
        expect(fixture.nativeElement.textContent).toContain('Conferma importazione');
    });

    it('shows the outcome stage in Italian', () => {
        component['job'] = { ...readyJob, status: 'COMPLETED', stage: 'COMPLETED' };
        component['activeStep'] = 5;
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('Elaborazione completata');
        expect(fixture.nativeElement.textContent).not.toContain('COMPLETED');
    });

    it('shows recent import statuses in Italian', () => {
        component['job'] = undefined;
        component['recentJobs'] = [readyJob, { ...readyJob, id: 43, status: 'COMPLETED', stage: 'COMPLETED' }];
        component['activeStep'] = 1;
        fixture.detectChanges();

        const text = fixture.nativeElement.textContent;
        expect(text).toContain('Pronta');
        expect(text).toContain('Completata');
        expect(text).not.toContain('READY');
        expect(text).not.toContain('COMPLETED');
    });

    it('translates row actions, severities and problem codes', () => {
        expect(component['actionLabel']('CREATE')).toBe('Crea');
        expect(component['actionLabel']('REUSE')).toBe('Riutilizza');
        expect(component['actionLabel']('SKIP')).toBe('Ignora');
        expect(component['statusLabel']('ERROR')).toBe('Errore');
        expect(component['issueLabel']('REFERENCE_NOT_FOUND')).toBe('Riferimento non trovato');
        expect(component['issueLabel']('UNKNOWN_CODE')).toBe('Problema di importazione');
    });

    it('shows the organization name without the tenant code or label', () => {
        const text = fixture.nativeElement.textContent;

        expect(text).toContain('Banda test');
        expect(text).not.toContain('tenant attivo');
        expect(text).not.toContain('CODICE-NASCOSTO');
    });

    it('reads what the import left behind as soon as it has finished', () => {
        component['job'] = { ...readyJob, status: 'COMPLETED', stage: 'COMPLETED' };
        component['loadDetails']();
        component['activeStep'] = 5;
        fixture.detectChanges();

        expect(onboarding.outcome).toHaveBeenCalledWith(42);

        const text = fixture.nativeElement.textContent;
        expect(text).toContain('1 min 30 s');
        expect(text).toContain('Identità create');
        expect(text).toContain('Utenti');
    });

    it('does not read an outcome while the import is still working', () => {
        component['job'] = { ...readyJob, status: 'APPLYING', stage: 'PREPARING_IDENTITIES' };
        component['loadDetails']();

        expect(onboarding.outcome).not.toHaveBeenCalled();
    });

    it('offers only the sections of the modules active for the organization', () => {
        component['context'] = { ...context, availableSections: ['USERS'] };
        component['job'] = { ...readyJob, status: 'COMPLETED', stage: 'COMPLETED' };
        component['activeStep'] = 5;
        fixture.detectChanges();

        const links = Array.from<HTMLAnchorElement>(fixture.nativeElement.querySelectorAll('a')).map((link) => link.getAttribute('href'));
        expect(links).toContain('/users');
        expect(links).not.toContain('/inventory');
        expect(links).not.toContain('/finance');
    });

    it('keeps the key of a request lost on the network and forgets a refused one', () => {
        component['uploadKey'] = 'chiave';
        component['forgetKeyUnlessLost']({ status: 0 }, 'upload');
        expect(component['uploadKey']).toBe('chiave');

        component['forgetKeyUnlessLost']({ status: 409 }, 'upload');
        expect(component['uploadKey']).toBeUndefined();

        component['applyKey'] = 'chiave';
        component['forgetKeyUnlessLost']({ status: 504 }, 'apply');
        expect(component['applyKey']).toBe('chiave');
    });

    it('points a flagged value at the message that explains it', () => {
        const flagged: OnboardingRow = { id: 1, section: 'USERS', rowNumber: 7, status: 'ERROR', action: 'CREATE', values: { email: 'nome@' } };
        const problem: OnboardingIssue = { id: 9, severity: 'ERROR', code: 'VALUE_INVALID_EMAIL', section: 'USERS', rowNumber: 7, columnName: 'email', message: 'Indirizzo e-mail non valido.' };
        onboarding.rows.and.returnValue(of(page([flagged])));
        onboarding.issues.and.returnValue(of(page([problem])));
        component['job'] = { ...readyJob, counts: { total: 1, valid: 0, warnings: 0, errors: 1 }, status: 'INVALID' };
        component['loadDetails']();
        component['activeStep'] = 3;
        fixture.detectChanges();

        const value = fixture.nativeElement.querySelector('dd[aria-describedby="onboarding-problem-9"]');
        expect(value).withContext('Il valore segnalato non rimanda al suo problema').not.toBeNull();
        expect(fixture.nativeElement.querySelector('#onboarding-problem-9')?.textContent).toContain('Indirizzo e-mail non valido.');
    });

    it('shows when the organization was last configured', () => {
        component['context'] = { ...context, lastImport: { ...readyJob, status: 'COMPLETED', stage: 'COMPLETED' } };
        fixture.detectChanges();

        const text = fixture.nativeElement.textContent;
        expect(text).toContain('Ultima importazione');
        expect(text).toContain('Completata');
    });

    function buttonWithText(label: string): HTMLButtonElement {
        const button = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button')).find((item) => item.textContent?.includes(label));
        expect(button).withContext(`Pulsante "${label}" non trovato`).toBeDefined();
        return button!;
    }
});
