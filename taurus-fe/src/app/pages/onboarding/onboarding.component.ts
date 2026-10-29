import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription, forkJoin, switchMap, timer } from 'rxjs';
import { ImportsModule } from '../../imports';
import { OnboardingContext, OnboardingIssue, OnboardingJob, OnboardingOutcome, OnboardingRow, OnboardingRowStatus, OnboardingSection, OnboardingSectionSummary } from '../../module';
import { OnboardingService, withErrorSummary } from '../../service';
import { PaginatorModule } from 'primeng/paginator';
import { ProgressBarModule } from 'primeng/progressbar';
import { StepperModule } from 'primeng/stepper';

/** The shortest and the longest interval between two reads of a job in progress. */
const POLL_MIN = 2000;
const POLL_MAX = 15000;
const PROBLEM_PAGE_SIZE = 25;

@Component({
    selector: 'app-onboarding',
    standalone: true,
    imports: [ImportsModule, PaginatorModule, ProgressBarModule, StepperModule],
    templateUrl: './onboarding.component.html',
    styleUrl: './onboarding.component.scss'
})
export class OnboardingComponent implements OnInit {
    protected context?: OnboardingContext;
    protected recentJobs: OnboardingJob[] = [];
    protected job?: OnboardingJob;
    protected outcome?: OnboardingOutcome;
    protected summaries: OnboardingSectionSummary[] = [];
    protected preview: OnboardingRow[] = [];
    protected problems: OnboardingIssue[] = [];
    protected previewTotal = 0;
    protected problemTotal = 0;
    protected problemPage = 0;
    protected readonly problemPageSize = PROBLEM_PAGE_SIZE;
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
        FILE_EMBEDDED_OBJECT: 'Oggetti incorporati non consentiti',
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
        PLAYBACK_PROPOSED: 'Suono proposto dal nome',
        PLAYBACK_NOT_CONFIGURED: "Strumento da configurare per l'ascolto",
        PLAYBACK_NOT_APPLIED: 'Suono indicato non applicato',
        OPENING_BALANCE_NOT_ALLOWED: 'Saldo iniziale non consentito',
        KEYCLOAK_IDENTITY_WILL_BE_LINKED: 'Utenza Keycloak esistente collegata',
        SOURCE_INTEGRITY_FAILURE: 'Il file caricato non corrisponde più',
        TOO_MANY_ISSUES: 'Troppi problemi: elenco troncato',
        TOO_MANY_CELLS: 'Troppe celle nel file'
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
        CANCELLED: 'Importazione annullata',
        SOURCE_INTEGRITY_FAILURE: 'File non più corrispondente',
        PREFLIGHT_CONFLICT: 'Controlli prima dell’avvio non superati',
        FEATURE_DEPENDENCY_DISABLED: 'Funzionalità richiesta non attiva',
        VALIDATION_TECHNICAL_FAILURE: 'Validazione interrotta da un errore tecnico',
        APPLICATION_FAILED: 'Importazione non applicata',
        KEYCLOAK_PREPARE_FAILED: 'Preparazione delle identità non riuscita',
        KEYCLOAK_COMPENSATION_FAILED: 'Ripristino delle identità non riuscito',
        COMPENSATION_INCOMPLETE: 'Ripristino incompleto',
        SETUP_EMAIL_FAILED: 'E-mail di configurazione non inviate',
        ABANDONED: 'Importazione scaduta senza conferma'
    };
    private polling?: Subscription;
    private pollingId?: number;
    private pollDelay = POLL_MIN;
    private jobEtag?: string;
    /** Kept across a retry so a request lost on the network is not applied twice by the second attempt. */
    private uploadKey?: string;
    private applyKey?: string;
    private readonly destroyRef = inject(DestroyRef);
    private readonly onVisibilityChange = () => {
        if (document.hidden) {
            this.polling?.unsubscribe();
            return;
        }
        if (this.pollingId && this.job && this.inProgress(this.job)) {
            this.pollDelay = POLL_MIN;
            this.poll(0);
        }
    };

    constructor(
        private readonly onboarding: OnboardingService,
        private readonly route: ActivatedRoute,
        private readonly router: Router
    ) {
        this.destroyRef.onDestroy(() => this.stopPolling());
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
        this.uploadKey ??= crypto.randomUUID();
        this.onboarding.upload(this.selectedFile, this.format, this.format === 'CSV' ? this.csvSection : undefined, this.format === 'XLSX' ? this.selectedSections : [], this.uploadKey, withErrorSummary('Caricamento non riuscito')).subscribe({
            next: (job) => {
                this.busy = false;
                this.uploadKey = undefined;
                this.selectedFile = undefined;
                this.jobEtag = undefined;
                this.job = job;
                this.activeStep = 3;
                this.router.navigate(['/onboarding/imports', job.id], { replaceUrl: true });
                this.startPolling(job.id);
            },
            error: (error) => {
                this.busy = false;
                this.forgetKeyUnlessLost(error, 'upload');
            }
        });
    }
    protected apply(): void {
        if (!this.job || !this.canApply || this.busy) return;
        this.busy = true;
        this.applyKey ??= crypto.randomUUID();
        this.onboarding.apply(this.job.id, this.warningsAccepted, this.sendSetupEmails, this.applyKey, withErrorSummary('Importazione non avviata')).subscribe({
            next: (job) => {
                this.busy = false;
                this.applyKey = undefined;
                this.job = job;
                this.activeStep = 5;
                this.startPolling(job.id);
            },
            error: (error) => {
                this.busy = false;
                this.forgetKeyUnlessLost(error, 'apply');
            }
        });
    }
    /**
     * A request lost before an answer keeps its key, so the retry is recognized as the same attempt instead of
     * uploading the file twice or starting a second application. An answered refusal forgets it: the next attempt is
     * a different one.
     */
    private forgetKeyUnlessLost(error: unknown, command: 'upload' | 'apply'): void {
        const status = (error as { status?: number } | undefined)?.status;
        if (status === 0 || status === undefined || status === 504) return;
        if (command === 'upload') this.uploadKey = undefined;
        else this.applyKey = undefined;
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
        this.problemPage = page;
        this.onboarding.issues(this.job.id, undefined, this.sectionFilter, page, PROBLEM_PAGE_SIZE).subscribe((result) => {
            this.problems = result.content;
            this.problemTotal = result.totalElements;
        });
    }
    /** The page chosen in the problems paginator: a file with thousands of problems is read a page at a time. */
    protected onProblemPage(event: { first?: number; rows?: number }): void {
        this.loadProblems(Math.floor((event.first ?? 0) / (event.rows ?? PROBLEM_PAGE_SIZE)));
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
    /**
     * The problem shown on a value among those listed below, so the cell can point to its message with
     * aria-describedby: a screen reader then reads the reason together with the value instead of a lone colour.
     */
    protected problemOf(row: OnboardingRow, column: string): OnboardingIssue | undefined {
        return this.problems.find((issue) => issue.rowNumber === row.rowNumber && issue.columnName === column && (!issue.section || issue.section === row.section));
    }
    protected problemId(issue?: OnboardingIssue): string | null {
        return issue ? `onboarding-problem-${issue.id}` : null;
    }
    /** How long the import took, from the first write to the last one. */
    protected durationLabel(millis?: number): string {
        if (millis === undefined || millis === null) return 'non disponibile';
        const seconds = Math.round(millis / 1000);
        if (seconds < 60) return `${seconds} s`;
        const minutes = Math.floor(seconds / 60);
        return `${minutes} min ${String(seconds % 60).padStart(2, '0')} s`;
    }
    /** Whether the module that owns the section is active for the tenant: its link is offered only then. */
    protected moduleActive(section: OnboardingSection): boolean {
        return !!this.context?.availableSections.includes(section);
    }
    protected get appliedSections(): OnboardingSectionSummary[] {
        return (this.outcome?.sections ?? []).filter((section) => section.total > 0);
    }

    private loadContext(): void {
        const context = withErrorSummary('Onboarding non disponibile');
        forkJoin({ context: this.onboarding.context(context), jobs: this.onboarding.jobs(0, 10, context) }).subscribe({
            next: ({ context, jobs }) => {
                this.context = context;
                this.recentJobs = jobs.content;
                this.sections = this.sections.filter((section) => context.availableSections.includes(section.value));
                this.selectedSections = this.selectedSections.filter((section) => context.availableSections.includes(section));
                if (!context.availableSections.includes(this.csvSection)) this.csvSection = context.availableSections[0] ?? 'USERS';
            },
            error: () => undefined
        });
    }
    private loadJob(id: number, poll: boolean): void {
        this.jobEtag = undefined;
        this.onboarding.job(id).subscribe({
            next: ({ job, etag }) => {
                if (!job) return;
                this.jobEtag = etag;
                this.job = job;
                this.syncStep();
                this.loadDetails();
                if (poll && this.inProgress(job)) this.startPolling(id);
            },
            error: () => this.router.navigate(['/onboarding'])
        });
    }
    /**
     * Follows the import while it works. The interval grows while nothing moves and goes back to the shortest one as
     * soon as the phase changes; a hidden page polls nothing at all and reads again as soon as it comes back.
     */
    private startPolling(id: number): void {
        this.stopPolling();
        this.pollingId = id;
        this.pollDelay = POLL_MIN;
        document.addEventListener('visibilitychange', this.onVisibilityChange);
        this.poll(0);
    }
    private poll(delay: number): void {
        const id = this.pollingId;
        if (id === undefined) return;
        this.polling = timer(delay)
            .pipe(switchMap(() => this.onboarding.job(id, this.jobEtag)))
            .subscribe({
                next: ({ job, etag }) => {
                    this.jobEtag = etag;
                    if (job) {
                        const changedStage = job.stage !== this.job?.stage;
                        this.job = job;
                        this.syncStep();
                        if (changedStage || !this.inProgress(job)) this.loadDetails();
                        this.pollDelay = changedStage ? POLL_MIN : this.slower();
                        if (!this.inProgress(job)) {
                            this.stopPolling();
                            return;
                        }
                    } else {
                        this.pollDelay = this.slower();
                    }
                    if (!document.hidden) this.poll(this.pollDelay);
                },
                error: () => this.stopPolling()
            });
    }
    private slower(): number {
        return Math.min(POLL_MAX, Math.round(this.pollDelay * 1.5));
    }
    private stopPolling(): void {
        this.polling?.unsubscribe();
        this.polling = undefined;
        this.pollingId = undefined;
        document.removeEventListener('visibilitychange', this.onVisibilityChange);
    }
    private loadDetails(): void {
        if (!this.job) return;
        this.problemPage = 0;
        forkJoin({
            sections: this.onboarding.sections(this.job.id),
            rows: this.onboarding.rows(this.job.id, this.sectionFilter, this.rowStatusFilter, 0, 50),
            issues: this.onboarding.issues(this.job.id, undefined, this.sectionFilter, 0, PROBLEM_PAGE_SIZE)
        }).subscribe(({ sections, rows, issues }) => {
            this.summaries = sections;
            this.preview = rows.content;
            this.previewTotal = rows.totalElements;
            this.problems = issues.content;
            this.problemTotal = issues.totalElements;
        });
        if (this.finished(this.job)) this.loadOutcome(this.job.id);
    }
    /** What the import left behind, read once it has finished: it is the closing report of the last step. */
    private loadOutcome(id: number): void {
        this.onboarding.outcome(id).subscribe({
            next: (outcome) => (this.outcome = outcome),
            error: () => (this.outcome = undefined)
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
    private finished(job: OnboardingJob): boolean {
        return ['COMPLETED', 'FAILED', 'COMPENSATION_REQUIRED'].includes(job.status);
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
