import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { OnboardingContext, OnboardingJob } from '../../module';
import { OnboardingService, ToastService } from '../../service';
import { OnboardingComponent } from './onboarding.component';

describe('OnboardingComponent', () => {
    let fixture: ComponentFixture<OnboardingComponent>;
    let component: OnboardingComponent;

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

    beforeEach(async () => {
        const emptyPage = {
            content: [],
            pageable: { pageNumber: 0, pageSize: 10, sort: { empty: true, sorted: false, unsorted: true }, offset: 0, paged: true, unpaged: false },
            last: true,
            totalPages: 0,
            totalElements: 0,
            size: 10,
            number: 0,
            sort: { empty: true, sorted: false, unsorted: true },
            first: true,
            numberOfElements: 0,
            empty: true
        };
        const onboarding = jasmine.createSpyObj<OnboardingService>('OnboardingService', ['context', 'jobs', 'rows']);
        onboarding.context.and.returnValue(of(context));
        onboarding.jobs.and.returnValue(of(emptyPage));
        onboarding.rows.and.returnValue(of(emptyPage));

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

    function buttonWithText(label: string): HTMLButtonElement {
        const button = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('button')).find((item) => item.textContent?.includes(label));
        expect(button).withContext(`Pulsante "${label}" non trovato`).toBeDefined();
        return button!;
    }
});
