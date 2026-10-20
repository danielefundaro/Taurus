import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { DialogService } from 'primeng/dynamicdialog';
import { finalize, first, map, Observable, of, switchMap } from 'rxjs';
import { shortHash, signatureActStatusLabel, signatureActStatusSeverity, signatureActTypeLabel, signatureDocumentLabel, signerRoleLabel, signerStatusLabel } from '../../constants';
import { SignatureActFlow } from '../../dialogs/signature-act-dialog/signature-act-flow';
import { ImportsModule } from '../../imports';
import { SignatureAct, SignatureActDocument, SignatureActOutcome, SignatureLinkAct } from '../../module';
import { PublicSignatureService, SignatureService, ToastService, withHandledErrors } from '../../service';

/** Errori della lettura del link mostrati nella pagina invece che in un avviso. */
const LINK_ERRORS = ['http.404', 'http.429', 'http.0'];

type PageState = 'loading' | 'unavailable' | 'ready' | 'completed';

/**
 * Pagina pubblica di firma del genitore o tutore senza account, aperta dal link ricevuto via e-mail: senza menu e senza
 * login. Mostra l'atto, raccoglie i dati dell'emissione e registra le firme con il codice inviato allo stesso indirizzo
 * o con CieSign, con le stesse finestre della firma dall'applicazione; ad atto firmato offre i documenti firmati e
 * l'attestazione.
 */
@Component({
    selector: 'app-signature-link',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './signature-link.component.html',
    styleUrl: './signature-link.component.scss',
    providers: [DialogService, PublicSignatureService, { provide: SignatureService, useExisting: PublicSignatureService }, SignatureActFlow]
})
export class SignatureLinkComponent implements OnInit {
    protected state: PageState = 'loading';
    protected problem: 'unavailable' | 'tooManyRequests' | 'network' = 'unavailable';
    protected link?: SignatureLinkAct;
    protected signing = false;
    protected downloading?: string;
    protected readonly actLabel = signatureActTypeLabel;
    protected readonly statusLabel = signatureActStatusLabel;
    protected readonly statusSeverity = signatureActStatusSeverity;
    protected readonly documentLabel = signatureDocumentLabel;
    protected readonly roleLabel = signerRoleLabel;
    protected readonly signerStatusLabel = signerStatusLabel;
    protected readonly shortHash = shortHash;

    constructor(
        private readonly route: ActivatedRoute,
        private readonly signatures: PublicSignatureService,
        private readonly flow: SignatureActFlow,
        private readonly toastService: ToastService,
        private readonly meta: Meta,
        private readonly title: Title
    ) {}

    ngOnInit(): void {
        // The address holds the token: it is never sent to other sites.
        this.meta.updateTag({ name: 'referrer', content: 'no-referrer' });
        this.title.setTitle('Firma elettronica');
        this.signatures.use(this.route.snapshot.paramMap.get('token') ?? '');
        this.load();
    }

    protected get act(): SignatureAct | undefined {
        return this.link?.act;
    }

    /** Il genitore deve ancora inserire i dati o firmare. */
    protected get toSign(): boolean {
        return !!this.act && (this.act.dataRequired || this.act.pendingPurposes.length > 0);
    }

    protected get waitingForOthers(): boolean {
        return !!this.act && this.act.status === 'AWAITING_SIGNATURES' && !this.toSign;
    }

    protected get signLabel(): string {
        if (this.act?.dataRequired) return 'Inserisci i dati e firma';
        return this.act?.pendingPurposes.length === 1 && this.act.pendingPurposes[0] === 'CLAUSE_APPROVAL' ? 'Approva le clausole' : 'Firma';
    }

    protected get documents(): SignatureActDocument[] {
        return this.act?.documents.filter((document) => document.available) ?? [];
    }

    protected load(): void {
        this.state = 'loading';
        this.signatures
            .getLinkAct(withHandledErrors(LINK_ERRORS))
            .pipe(first())
            .subscribe({
                next: (link) => {
                    this.link = link;
                    this.state = 'ready';
                },
                error: (error: HttpErrorResponse) => {
                    this.problem = error.status === 429 ? 'tooManyRequests' : error.status === 0 ? 'network' : 'unavailable';
                    this.state = 'unavailable';
                }
            });
    }

    /**
     * Firma passo dopo passo: dati, lettura del documento e codice o CieSign. Quando la firma completa l'atto il link non vale più e
     * l'e-mail di conferma porta un nuovo collegamento; altrimenti la pagina si aggiorna e prosegue con la firma
     * successiva, per esempio l'approvazione delle clausole.
     */
    protected sign(): void {
        const act = this.act;
        if (!act || this.signing) return;
        this.signing = true;
        this.signStep(act)
            .pipe(finalize(() => (this.signing = false)))
            .subscribe((outcomes) => {
                this.signing = false;
                if (outcomes?.some((outcome) => outcome.status === 'SIGNED')) {
                    this.state = 'completed';
                    return;
                }
                this.signatures
                    .getLinkAct(withHandledErrors(LINK_ERRORS))
                    .pipe(first())
                    .subscribe({
                        next: (link) => {
                            this.link = link;
                            if (!outcomes) return;
                            if (this.toSign) {
                                this.sign();
                                return;
                            }
                            this.toastService.success('Firma registrata', 'La tua firma è registrata; l’atto attende le altre firme.');
                        },
                        error: () => (this.state = 'unavailable')
                    });
            });
    }

    protected download(file: SignatureActDocument): void {
        const act = this.act;
        if (!act || this.downloading) return;
        this.downloading = file.role;
        this.signatures
            .downloadDocument(act.id, file.role)
            .pipe(
                first(),
                finalize(() => (this.downloading = undefined))
            )
            .subscribe((blob) => this.save(blob, this.fileName(act, file.role === 'CLAUSES' ? 'clausole' : 'foglio', !!file.sealedHash)));
    }

    protected downloadAttestation(): void {
        const act = this.act;
        if (!act || this.downloading) return;
        this.downloading = 'ATTESTATION';
        this.signatures
            .downloadAttestation()
            .pipe(
                first(),
                finalize(() => (this.downloading = undefined))
            )
            .subscribe((blob) => this.save(blob, this.fileName(act, 'attestazione', false)));
    }

    /** Esito della firma per l'atto: dalla conferma del codice o dal caricamento CieSign; vuoto se la firma non c'è stata. */
    private signStep(act: SignatureAct): Observable<SignatureActOutcome[] | undefined> {
        return this.flow.openAct(act).pipe(
            switchMap((result) => {
                if (result?.outcome) return of([result.outcome]);
                if (!result?.request) return of(undefined);
                return this.flow.confirmWith(result.request, result.act).pipe(map((confirmation) => confirmation?.acts));
            })
        );
    }

    private fileName(act: SignatureAct, suffix: string, signed: boolean): string {
        return `foglio-${(act.sheetNumber ?? '').replace('/', '-')}-${suffix}${signed ? '-firmato' : ''}.pdf`;
    }

    private save(blob: Blob, fileName: string): void {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = fileName;
        anchor.click();
        URL.revokeObjectURL(url);
    }
}
