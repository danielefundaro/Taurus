import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, debounce, finalize, first, map, Observable, of, Subject, Subscription, switchMap, timer } from 'rxjs';
import { DangerZoneOperation } from '../../../components/danger-zone/danger-zone.component';
import { LegalTextComponent } from '../../../components/legal-text/legal-text.component';
import {
    LEGAL_CHANGE_SUMMARY_MAX_LENGTH,
    LEGAL_CONTENT_MAX_LENGTH,
    LEGAL_DOCUMENT_ACTIONS,
    LEGAL_DOCUMENT_TYPES,
    LEGAL_VERSION_PATTERN,
    legalDocumentActionLabel,
    legalDocumentStateLabel,
    legalDocumentStateSeverity,
    legalDocumentTypeLabel
} from '../../../constants';
import { ImportsModule } from '../../../imports';
import { LegalDocument, LegalDocumentType } from '../../../module';
import { ConfirmService, legalDocumentPath, LegalService, ToastService, withErrorSummary, withHandledErrors } from '../../../service';
import { DetailPageBase } from '../../_shared/detail-page.base';

type EditorView = 'text' | 'preview';

/** Richiesta di anteprima: subito all'apertura, 500 ms dopo l'ultima modifica mentre si scrive. */
interface PreviewRequest {
    content: string;
    immediate: boolean;
}

const TITLE_MAX_LENGTH = 255;

const emptyDocument = (): LegalDocument => ({ documentType: 'TERMS', version: '', title: '', action: 'ACCEPT', required: true, content: '', changeSummary: '' });

/**
 * Una versione di un documento legale. La bozza si scrive con l'anteprima accanto, si salva e si pubblica; dalla
 * pubblicazione resta in sola lettura e si ritira, si riattiva o fa da base per una nuova versione. Un documento
 * pubblicato prima che il testo stesse in Taurus riceve il testo una sola volta.
 */
@Component({
    selector: 'app-legal-document-detail',
    standalone: true,
    imports: [ImportsModule, LegalTextComponent],
    templateUrl: './legal-document-detail.component.html',
    styleUrl: './legal-document-detail.component.scss'
})
export class LegalDocumentDetailComponent extends DetailPageBase implements OnInit, OnDestroy {
    protected override readonly formUnitLabel = 'documento';

    protected document: LegalDocument = emptyDocument();
    /** La versione come la conserva il server; vuota per un documento nuovo. */
    protected saved?: LegalDocument;
    protected validationAttempted = false;
    protected previewHtml = '';
    protected previewLoading = false;
    protected previewFailed = false;
    protected view: EditorView = 'text';

    protected readonly views: { label: string; value: EditorView }[] = [
        { label: 'Testo', value: 'text' },
        { label: 'Anteprima', value: 'preview' }
    ];
    protected readonly documentTypes = LEGAL_DOCUMENT_TYPES;
    protected readonly actions = LEGAL_DOCUMENT_ACTIONS;
    protected readonly contentMaxLength = LEGAL_CONTENT_MAX_LENGTH;
    protected readonly summaryMaxLength = LEGAL_CHANGE_SUMMARY_MAX_LENGTH;
    protected readonly typeLabel = legalDocumentTypeLabel;
    protected readonly actionLabel = legalDocumentActionLabel;
    protected readonly stateLabel = legalDocumentStateLabel;
    protected readonly stateSeverity = legalDocumentStateSeverity;
    protected readonly dangerOperations: DangerZoneOperation[] = [
        { id: 'delete', title: 'Elimina bozza', consequence: 'La bozza si elimina definitivamente. Una versione pubblicata invece non si elimina.', label: 'Elimina bozza', icon: 'pi pi-trash' }
    ];

    /** La versione in vigore di ogni tipo, per dire quale versione sostituisce una pubblicazione. */
    private activeVersions: Partial<Record<LegalDocumentType, LegalDocument>> = {};
    private readonly preview$ = new Subject<PreviewRequest>();
    private previewSubscription?: Subscription;

    constructor(
        private readonly route: ActivatedRoute,
        private readonly router: Router,
        private readonly legalService: LegalService,
        private readonly confirmService: ConfirmService,
        private readonly toastService: ToastService
    ) {
        super();
    }

    ngOnInit(): void {
        this.watchBrowserExit();
        this.previewSubscription = this.preview$
            .pipe(
                debounce((request) => timer(request.immediate ? 0 : 500)),
                switchMap((request) => this.render(request.content))
            )
            .subscribe((html) => {
                if (html === undefined) return;
                this.previewHtml = html;
                this.previewFailed = false;
            });
        this.loadActiveVersions();

        const id = Number(this.route.snapshot.paramMap.get('id'));
        const source = Number(this.route.snapshot.queryParamMap.get('from'));
        if (id) this.load(id);
        else if (source) this.copyFrom(source);
    }

    ngOnDestroy(): void {
        this.releaseBrowserExit();
        this.previewSubscription?.unsubscribe();
    }

    protected get isNew(): boolean {
        return !this.saved?.id;
    }

    protected get isDraft(): boolean {
        return this.isNew || this.saved?.state === 'DRAFT';
    }

    /** Pubblicato con il solo indirizzo: il testo si inserisce una volta, il resto non cambia. */
    protected get completing(): boolean {
        return !this.isDraft && !this.saved?.contentSha256 && !!this.saved?.url;
    }

    protected get contentEditable(): boolean {
        return this.isDraft || this.completing;
    }

    protected get heading(): string {
        return this.document.title.trim() || (this.isNew ? 'Nuovo documento' : 'Documento legale');
    }

    protected get publicPath(): string {
        return legalDocumentPath(this.document.documentType, this.saved?.version);
    }

    protected get contentLength(): string {
        return (this.document.content?.length ?? 0).toLocaleString('it-IT');
    }

    protected get versionError(): string | undefined {
        const version = this.document.version.trim();
        if (!version) return 'Indica la versione del documento.';
        return LEGAL_VERSION_PATTERN.test(version) ? undefined : 'Usa solo lettere, numeri, punto, trattino e trattino basso, iniziando con una lettera o un numero, fino a 32 caratteri.';
    }

    protected get titleError(): string | undefined {
        const title = this.document.title.trim();
        if (!title) return 'Indica il titolo del documento.';
        return title.length > TITLE_MAX_LENGTH ? `Il titolo supera i ${TITLE_MAX_LENGTH} caratteri.` : undefined;
    }

    protected get contentError(): string | undefined {
        const content = this.document.content ?? '';
        if (!content.trim()) return 'Scrivi il testo del documento.';
        return content.length > LEGAL_CONTENT_MAX_LENGTH ? 'Il testo supera i 100.000 caratteri.' : undefined;
    }

    protected get summaryError(): string | undefined {
        return (this.document.changeSummary ?? '').length > LEGAL_CHANGE_SUMMARY_MAX_LENGTH ? 'La sintesi supera i 1.000 caratteri.' : undefined;
    }

    protected get invalidCount(): number {
        return [this.versionError, this.titleError, this.contentError, this.summaryError].filter(Boolean).length;
    }

    protected documentTypeChanged(documentType: LegalDocumentType): void {
        this.document.action = documentType === 'PRIVACY' ? 'ACKNOWLEDGE' : 'ACCEPT';
        this.isDirty = true;
    }

    protected contentChanged(content: string): void {
        this.isDirty = true;
        this.preview$.next({ content, immediate: false });
    }

    protected save(): void {
        if (!this.valid() || this.saving) return;
        this.saving = true;
        this.persist()
            .pipe(
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe({
                next: (document) => {
                    this.toastService.success('Documenti legali', 'Bozza salvata.');
                    this.show(document);
                },
                error: () => undefined
            });
    }

    protected publish(): void {
        if (!this.valid() || this.saving) return;
        const version = this.document.version.trim();
        const replaced = this.activeVersions[this.document.documentType];
        const change = replaced && replaced.id !== this.saved?.id ? `La versione ${version} sostituisce la ${replaced.version}.` : `La versione ${version} entra in vigore.`;
        const effect = this.document.required ? 'Da ora il testo non si può più modificare e gli utenti dovranno confermarlo al prossimo accesso.' : 'Da ora il testo non si può più modificare e sarà visibile a tutti.';
        this.confirmService.confirmReversible({
            title: `Pubblicare la versione ${version}?`,
            consequence: `${change} ${effect}`,
            actionLabel: 'Pubblica',
            accept: () => {
                this.saving = true;
                const draft = this.isNew || this.isDirty ? this.persist() : of(this.saved!);
                draft
                    .pipe(
                        switchMap((document) => this.legalService.activateDocument(document.id!, withErrorSummary('Pubblicazione non riuscita'))),
                        first(),
                        finalize(() => (this.saving = false))
                    )
                    .subscribe({
                        next: (document) => {
                            this.toastService.success('Documenti legali', `Versione ${document.version} pubblicata.`);
                            this.activeVersions[document.documentType] = document;
                            this.show(document);
                        },
                        error: () => undefined
                    });
            }
        });
    }

    /** Inserisce una sola volta il testo di un documento pubblicato con il solo indirizzo. */
    protected completeText(): void {
        if (this.contentError || this.saving || !this.saved?.id) return;
        const saved = this.saved;
        this.confirmService.confirmReversible({
            title: 'Inserire il testo?',
            consequence: `Il testo della versione ${saved.version} si inserisce una sola volta e poi non si modifica più. Deve essere identico a quello pubblicato a ${saved.url}: chi aveva confermato la versione resta in regola.`,
            actionLabel: 'Inserisci il testo',
            accept: () => {
                this.saving = true;
                this.legalService
                    .updateDocument(saved.id!, this.payload(saved), withErrorSummary('Salvataggio non riuscito'))
                    .pipe(
                        first(),
                        finalize(() => (this.saving = false))
                    )
                    .subscribe({
                        next: (document) => {
                            this.toastService.success('Documenti legali', 'Testo inserito.');
                            this.show(document);
                        },
                        error: () => undefined
                    });
            }
        });
    }

    protected withdraw(): void {
        const saved = this.saved;
        if (!saved?.id || this.saving) return;
        this.confirmService.confirmReversible({
            title: `Ritirare la versione ${saved.version}?`,
            consequence: 'Senza una versione in vigore nessuno dovrà più confermare questo documento e la sua pagina pubblica risponderà «Documento non disponibile». La versione resta leggibile al suo indirizzo e si può riattivare.',
            actionLabel: 'Ritira',
            accept: () => this.changeActivation(this.legalService.withdrawDocument(saved.id!, withErrorSummary('Ritiro non riuscito')), `Versione ${saved.version} ritirata.`)
        });
    }

    protected reactivate(): void {
        const saved = this.saved;
        if (!saved?.id || this.saving) return;
        const active = this.activeVersions[saved.documentType];
        const change = active && active.id !== saved.id ? `La versione ${saved.version} torna in vigore al posto della ${active.version}.` : `La versione ${saved.version} torna in vigore.`;
        const effect = saved.required ? ' Chi l’aveva già confermata resta in regola; gli altri dovranno confermarla al prossimo accesso.' : '';
        this.confirmService.confirmReversible({
            title: `Riattivare la versione ${saved.version}?`,
            consequence: change + effect,
            actionLabel: 'Riattiva',
            accept: () => this.changeActivation(this.legalService.activateDocument(saved.id!, withErrorSummary('Riattivazione non riuscita')), `Versione ${saved.version} di nuovo in vigore.`)
        });
    }

    /** Apre una bozza nuova con tipo, titolo, conferma, obbligatorietà e testo di questa versione. */
    protected newVersion(): void {
        if (this.saved?.id) void this.router.navigate(['/legal-documents/new'], { queryParams: { from: this.saved.id } });
    }

    protected executeDanger(operation: string): void {
        const saved = this.saved;
        if (operation !== 'delete' || !saved?.id || !this.isDraft) return;
        this.confirmService.confirmDestructive({
            title: 'Eliminare la bozza?',
            consequence: `La bozza della versione ${saved.version} si elimina definitivamente.`,
            actionLabel: 'Elimina bozza',
            accept: () =>
                this.legalService
                    .deleteDocument(saved.id!, withErrorSummary('Eliminazione non riuscita'))
                    .pipe(first())
                    .subscribe({
                        next: () => {
                            this.clearDirtyUnits();
                            this.toastService.success('Documenti legali', 'Bozza eliminata.');
                            void this.router.navigate(['/legal-documents']);
                        },
                        error: () => undefined
                    })
        });
    }

    private load(id: number): void {
        this.loading = true;
        this.legalService
            .getDocument(id, withErrorSummary('Caricamento non riuscito'))
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe({ next: (document) => this.show(document), error: () => undefined });
    }

    private copyFrom(id: number): void {
        this.loading = true;
        this.legalService
            .getDocument(id, withErrorSummary('Caricamento non riuscito'))
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe({
                next: (source) => {
                    this.document = { ...emptyDocument(), documentType: source.documentType, title: source.title, action: source.action, required: source.required, content: source.content ?? '' };
                    this.preview$.next({ content: this.document.content ?? '', immediate: true });
                },
                error: () => undefined
            });
    }

    private loadActiveVersions(): void {
        this.legalService
            .getDocuments(withHandledErrors(['*']))
            .pipe(first())
            .subscribe({
                next: (documents) => (this.activeVersions = Object.fromEntries(documents.filter((document) => document.state === 'ACTIVE').map((document) => [document.documentType, document]))),
                error: () => undefined
            });
    }

    /** Mostra la versione salvata; un documento appena creato prende il suo indirizzo. */
    private show(document: LegalDocument): void {
        const created = this.isNew;
        this.saved = document;
        this.document = { ...document, content: document.content ?? '', changeSummary: document.changeSummary ?? '' };
        this.validationAttempted = false;
        this.clearDirtyUnits();
        this.preview$.next({ content: this.document.content ?? '', immediate: true });
        if (created && document.id) void this.router.navigate(['/legal-documents', document.id], { replaceUrl: true });
    }

    private changeActivation(request: Observable<LegalDocument>, message: string): void {
        this.saving = true;
        request
            .pipe(
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe({
                next: (document) => {
                    this.toastService.success('Documenti legali', message);
                    this.loadActiveVersions();
                    this.show(document);
                },
                error: () => undefined
            });
    }

    private persist(): Observable<LegalDocument> {
        const context = withErrorSummary('Salvataggio non riuscito');
        const payload = this.payload(this.document);
        return this.saved?.id ? this.legalService.updateDocument(this.saved.id, payload, context) : this.legalService.createDocument(payload, context);
    }

    /** I dettagli di `source` con il testo scritto nella pagina. */
    private payload(source: LegalDocument): LegalDocument {
        return {
            documentType: source.documentType,
            version: source.version.trim(),
            title: source.title.trim(),
            action: source.action,
            required: source.required,
            content: this.document.content ?? '',
            changeSummary: source.changeSummary?.trim() || null
        };
    }

    private valid(): boolean {
        this.validationAttempted = true;
        return this.invalidCount === 0;
    }

    /** L'HTML del testo; `undefined` quando l'anteprima non riesce e resta quella precedente. */
    private render(content: string): Observable<string | undefined> {
        if (!content.trim()) return of('');
        this.previewLoading = true;
        return this.legalService.preview(content, withHandledErrors(['*'])).pipe(
            map((html): string | undefined => html),
            catchError(() => {
                this.previewFailed = true;
                return of(undefined);
            }),
            finalize(() => (this.previewLoading = false))
        );
    }
}
