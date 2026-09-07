import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription, forkJoin, interval, startWith, switchMap } from 'rxjs';
import { ImportsModule } from '../../imports';
import { OnboardingContext, OnboardingIssue, OnboardingJob, OnboardingRow, OnboardingRowStatus, OnboardingSection, OnboardingSectionSummary } from '../../module';
import { OnboardingService, ToastService } from '../../service';
import { ProgressBarModule } from 'primeng/progressbar';
import { StepperModule } from 'primeng/stepper';

@Component({
    selector: 'app-onboarding',
    standalone: true,
    imports: [ImportsModule, ProgressBarModule, StepperModule],
    templateUrl: './onboarding.component.html',
    styleUrl: './onboarding.component.scss'
})
export class OnboardingComponent implements OnInit {
    protected context?: OnboardingContext;
    protected recentJobs: OnboardingJob[] = [];
    protected job?: OnboardingJob;
    protected summaries: OnboardingSectionSummary[] = [];
    protected preview: OnboardingRow[] = [];
    protected problems: OnboardingIssue[] = [];
    protected previewTotal = 0;
    protected problemTotal = 0;
    protected selectedFile?: File;
    protected format: 'XLSX' | 'CSV' = 'XLSX';
    protected csvSection: OnboardingSection = 'USERS';
    protected selectedSections: OnboardingSection[] = ['INSTRUMENTS', 'USERS', 'INVENTORY', 'CATEGORIES', 'ACCOUNTS', 'OPENING_BALANCES'];
    protected sectionFilter?: OnboardingSection;
    protected rowStatusFilter?: OnboardingRowStatus;
    protected warningsAccepted = false;
    protected sendSetupEmails = true;
    protected activeStep = 1;
    protected busy = false;
    protected sections = [
        { value: 'INSTRUMENTS' as OnboardingSection, label: 'Strumenti' },
        { value: 'USERS' as OnboardingSection, label: 'Utenti' },
        { value: 'INVENTORY' as OnboardingSection, label: 'Inventario' },
        { value: 'CATEGORIES' as OnboardingSection, label: 'Categorie' },
        { value: 'ACCOUNTS' as OnboardingSection, label: 'Conti' },
        { value: 'OPENING_BALANCES' as OnboardingSection, label: 'Saldi iniziali' }
    ];
    protected readonly statusOptions = [
        { value: 'VALID', label: 'Valida' },
        { value: 'WARNING', label: 'Avviso' },
        { value: 'ERROR', label: 'Errore' },
        { value: 'APPLIED', label: 'Applicata' },
        { value: 'SKIPPED', label: 'Saltata' }
    ];
    private readonly statusLabels: Record<string, string> = {
        UPLOADED: 'Caricata',
        VALIDATING: 'In validazione',
        INVALID: 'Non valida',
        READY: 'Pronta',
        APPLYING: 'In importazione',
        COMPENSATING: 'Ripristino in corso',
        COMPLETED: 'Completata',
        FAILED: 'Non riuscita',
        COMPENSATION_REQUIRED: 'Ripristino richiesto',
        CANCELLED: 'Annullata',
        VALID: 'Valida',
        WARNING: 'Avviso',
        ERROR: 'Errore',
        APPLIED: 'Applicata',
        SKIPPED: 'Saltata'
    };
    private readonly issueLabels: Record<string, string> = {
        FILE_EMPTY: 'File vuoto',
        FILE_TOO_LARGE: 'File troppo grande',
        CSV_SECTION_REQUIRED: 'Sezione CSV mancante',
        FILE_ENCODING_INVALID: 'Codifica del file non valida',
        FILE_CSV_INVALID: 'File CSV non valido',
        FILE_UNSUPPORTED_FORMAT: 'Formato del file non supportato',
        MACRO_NOT_ALLOWED: 'Macro non consentite',
        FILE_EXTERNAL_LINKS: 'Collegamenti esterni non consentiti',
        TEMPLATE_VERSION_UNSUPPORTED: 'Versione del modello non supportata',
        SHEET_HEADER_MISSING: 'Intestazione del foglio mancante',
        FORMULA_NOT_ALLOWED: 'Formule non consentite',
        CELL_TOO_LONG: 'Contenuto della cella troppo lungo',
        SHEET_REQUIRED: 'Foglio obbligatorio mancante',
        FILE_XLSX_INVALID: 'File XLSX non valido',
        TOO_MANY_COLUMNS: 'Troppe colonne',
        COLUMN_UNKNOWN: 'Colonna sconosciuta',
        COLUMN_MISMATCH: 'Intestazioni non corrispondenti',
        TOO_MANY_ROWS: 'Troppe righe',
        TOO_MANY_USER_ROWS: 'Troppi utenti',
        FILE_NO_DATA: 'Nessun dato da importare',
        SHEET_UNKNOWN: 'Foglio sconosciuto',
        DUPLICATE_IN_FILE: 'Valore duplicato nel file',
        VALUE_REQUIRED: 'Valore obbligatorio',
        VALUE_INVALID_EMAIL: 'Indirizzo e-mail non valido',
        VALUE_INVALID_ENUM: 'Valore non ammesso',
        VALUE_NOT_ALLOWED: 'Valore non consentito',
        VALUE_TOO_LONG: 'Valore troppo lungo',
        VALUE_INVALID_FORMAT: 'Formato non valido',
        VALUE_INVALID_CURRENCY: 'Valuta non valida',
        CONFLICT_EXISTING_RECORD: 'Conflitto con un dato esistente',
        REFERENCE_NOT_FOUND: 'Riferimento non trovato',
        TENANT_USER_LIMIT_EXCEEDED: 'Limite utenti superato',
        EXISTING_RECORD_WILL_BE_REUSED: 'Dato esistente riutilizzato',
        EXISTING_RECORD_WILL_BE_SKIPPED: 'Dato esistente ignorato',
        OPENING_BALANCE_NOT_ALLOWED: 'Saldo iniziale non consentito'
    };
    private readonly stageLabels: Record<string, string> = {
        QUEUED_FOR_VALIDATION: 'In attesa di validazione',
        INSPECTING_FILE: 'Analisi del file',
        VALIDATING_ROWS: 'Validazione delle righe',
        VALIDATION_COMPLETED: 'Validazione completata',
        VALIDATION_FAILED: 'Validazione non superata',
        FILE_REJECTED: 'File non accettato',
        PREPARING_IDENTITIES: 'Preparazione delle identità',
        COMPENSATING_IDENTITIES: 'Ripristino delle identità',
        COMPLETED: 'Elaborazione completata',
        COMPENSATED: 'Modifiche annullate correttamente',
        COMPENSATION_REQUIRED: 'Ripristino manuale richiesto',
        CANCELLED: 'Importazione annullata'
    };
    private polling?: Subscription;
    private readonly destroyRef = inject(DestroyRef);

    constructor(
        private readonly onboarding: OnboardingService,
        private readonly toast: ToastService,
        private readonly route: ActivatedRoute,
        private readonly router: Router
    ) {
        this.destroyRef.onDestroy(() => this.polling?.unsubscribe());
    }
    get isDirty(): boolean {
        return !!this.selectedFile && !this.job;
    }
    get dirtyUnitLabels(): string[] {
        return this.selectedFile ? [this.selectedFile.name] : [];
    }
    protected get locked(): boolean {
        return this.job?.status === 'APPLYING' || this.job?.status === 'COMPENSATING';
    }
    protected get canApply(): boolean {
        return this.job?.status === 'READY' && this.job.counts.errors === 0 && (this.job.counts.warnings === 0 || this.warningsAccepted);
    }

    ngOnInit(): void {
        const id = Number(this.route.snapshot.paramMap.get('id'));
        this.loadContext();
        if (Number.isFinite(id) && id > 0) this.loadJob(id, true);
    }
    protected selectFile(event: { files: File[]; currentFiles: File[] }): void {
        this.selectedFile = event.currentFiles?.[0] ?? event.files?.[0];
    }
    protected upload(): void {
        if (!this.selectedFile || this.busy) return;
        this.busy = true;
        this.onboarding.upload(this.selectedFile, this.format, this.format === 'CSV' ? this.csvSection : undefined, this.format === 'XLSX' ? this.selectedSections : [], crypto.randomUUID()).subscribe({
            next: (job) => {
                this.busy = false;
                this.selectedFile = undefined;
                this.job = job;
                this.activeStep = 3;
                this.router.navigate(['/onboarding/imports', job.id], { replaceUrl: true });
                this.startPolling(job.id);
            },
            error: () => {
                this.busy = false;
                this.toast.error('Caricamento non riuscito', 'Controlla formato e dimensione del file, quindi riprova.');
            }
        });
    }
    protected apply(): void {
        if (!this.job || !this.canApply || this.busy) return;
        this.busy = true;
        this.onboarding.apply(this.job.id, this.warningsAccepted, this.sendSetupEmails, crypto.randomUUID()).subscribe({
            next: (job) => {
                this.busy = false;
                this.job = job;
                this.activeStep = 5;
                this.startPolling(job.id);
            },
            error: () => {
                this.busy = false;
                this.toast.error('Importazione non avviata', 'Lo stato del tenant o del job è cambiato. Ricarica la verifica.');
            }
        });
    }
    protected retryValidation(): void {
        if (!this.job) return;
        this.onboarding.retryValidation(this.job.id).subscribe((job) => {
            this.job = job;
            this.startPolling(job.id);
        });
    }
    protected retryCompensation(): void {
        if (!this.job) return;
        this.onboarding.retryCompensation(this.job.id).subscribe((job) => (this.job = job));
    }
    protected retryEmails(): void {
        if (!this.job) return;
        this.onboarding.retryEmails(this.job.id).subscribe((job) => (this.job = job));
    }
    protected loadPreview(page = 0): void {
        if (!this.job) return;
        this.onboarding.rows(this.job.id, this.sectionFilter, this.rowStatusFilter, page, 50).subscribe((result) => {
            this.preview = result.content;
            this.previewTotal = result.totalElements;
        });
    }
    protected loadProblems(page = 0): void {
        if (!this.job) return;
        this.onboarding.issues(this.job.id, undefined, this.sectionFilter, page, 50).subscribe((result) => {
            this.problems = result.content;
            this.problemTotal = result.totalElements;
        });
    }
    protected resetFilters(): void {
        this.sectionFilter = undefined;
        this.rowStatusFilter = undefined;
        this.loadPreview();
        this.loadProblems();
    }
    protected open(job: OnboardingJob): void {
        this.router.navigate(['/onboarding/imports', job.id]);
    }
    protected downloadXlsx(): void {
        this.onboarding.templateXlsx().subscribe((blob) => this.download(blob, 'taurus-onboarding-v1.xlsx'));
    }
    protected downloadCsv(section: OnboardingSection): void {
        this.onboarding.templateCsv(section).subscribe((blob) => this.download(blob, `taurus-onboarding-v1-${section.toLowerCase()}.csv`));
    }
    protected downloadReport(): void {
        if (this.job) this.onboarding.report(this.job.id, this.job.status === 'COMPLETED').subscribe((blob) => this.download(blob, `taurus-onboarding-report-${this.job!.id}.xlsx`));
    }
    protected sectionLabel(value?: OnboardingSection): string {
        return this.sections.find((item) => item.value === value)?.label ?? 'Generale';
    }
    protected statusLabel(value: string): string {
        return this.statusLabels[value] ?? 'Stato non disponibile';
    }
    protected actionLabel(value: OnboardingRow['action']): string {
        return { CREATE: 'Crea', REUSE: 'Riutilizza', SKIP: 'Ignora' }[value];
    }
    protected issueLabel(value: string): string {
        return this.issueLabels[value] ?? 'Problema di importazione';
    }
    protected stageLabel(value: string): string {
        return this.stageLabels[value] ?? 'Aggiornamento dell’importazione in corso';
    }
    protected severity(value: string): 'success' | 'secondary' | 'info' | 'warn' | 'danger' {
        if (value === 'ERROR' || value === 'FAILED' || value === 'COMPENSATION_REQUIRED') return 'danger';
        if (value === 'WARNING' || value === 'INVALID') return 'warn';
        if (value === 'VALID' || value === 'COMPLETED' || value === 'READY' || value === 'APPLIED') return 'success';
        if (value === 'SKIPPED') return 'secondary';
        return 'info';
    }
    protected entries(row: OnboardingRow): Array<{ key: string; value: unknown }> {
        return Object.entries(row.values).map(([key, value]) => ({ key, value }));
    }

    private loadContext(): void {
        forkJoin({ context: this.onboarding.context(), jobs: this.onboarding.jobs(0, 10) }).subscribe({
            next: ({ context, jobs }) => {
                this.context = context;
                this.recentJobs = jobs.content;
                this.sections = this.sections.filter((section) => context.availableSections.includes(section.value));
                this.selectedSections = this.selectedSections.filter((section) => context.availableSections.includes(section));
                if (!context.availableSections.includes(this.csvSection)) this.csvSection = context.availableSections[0] ?? 'USERS';
            },
            error: () => this.toast.error('Onboarding non disponibile', 'Verifica di avere accesso alla configurazione iniziale.')
        });
    }
    private loadJob(id: number, poll: boolean): void {
        this.onboarding.job(id).subscribe({
            next: (job) => {
                this.job = job;
                this.syncStep();
                this.loadDetails();
                if (poll && this.inProgress(job)) this.startPolling(id);
            },
            error: () => this.router.navigate(['/onboarding'])
        });
    }
    private startPolling(id: number): void {
        this.polling?.unsubscribe();
        this.polling = interval(2000)
            .pipe(
                startWith(0),
                switchMap(() => this.onboarding.job(id))
            )
            .subscribe((job) => {
                const changedStage = job.stage !== this.job?.stage;
                this.job = job;
                this.syncStep();
                if (changedStage || !this.inProgress(job)) this.loadDetails();
                if (!this.inProgress(job)) this.polling?.unsubscribe();
            });
    }
    private loadDetails(): void {
        if (!this.job) return;
        forkJoin({
            sections: this.onboarding.sections(this.job.id),
            rows: this.onboarding.rows(this.job.id, this.sectionFilter, this.rowStatusFilter, 0, 50),
            issues: this.onboarding.issues(this.job.id, undefined, this.sectionFilter, 0, 50)
        }).subscribe(({ sections, rows, issues }) => {
            this.summaries = sections;
            this.preview = rows.content;
            this.previewTotal = rows.totalElements;
            this.problems = issues.content;
            this.problemTotal = issues.totalElements;
        });
    }
    private syncStep(): void {
        if (!this.job) return;
        if (this.job.status === 'COMPLETED' || this.job.status === 'FAILED' || this.job.status === 'COMPENSATION_REQUIRED') this.activeStep = 5;
        else if (this.job.status === 'APPLYING' || this.job.status === 'COMPENSATING') this.activeStep = 5;
        else if (this.job.status === 'READY') this.activeStep = 4;
        else this.activeStep = 3;
    }
    private inProgress(job: OnboardingJob): boolean {
        return ['UPLOADED', 'VALIDATING', 'APPLYING', 'COMPENSATING'].includes(job.status);
    }
    private download(blob: Blob, name: string): void {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = name;
        anchor.click();
        URL.revokeObjectURL(url);
    }
}
