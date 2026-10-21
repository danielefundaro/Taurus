import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { finalize, first } from 'rxjs';
import { SIGNATURE_HOLDER_FIELDS, shortHash, signatureActTypeLabel, signatureDocumentLabel, signaturePurposeLabel, toLocalDate } from '../../constants';
import { ImportsModule } from '../../imports';
import { SignatureAct, SignatureActDocument, SignatureActOutcome, SignatureCodeRequest, SignatureDocumentRole, SignaturePurpose } from '../../module';
import { ApiErrorService, SignatureService, withErrorSummary, withHandledErrors } from '../../service';

export interface SignatureActDialogData {
    act: SignatureAct;
}

/**
 * Esito della finestra: l'atto aggiornato e, quando l'utente sceglie di firmare, la conferma da chiedere con il codice
 * oppure l'esito della firma con CieSign, già registrata.
 */
export interface SignatureActDialogResult {
    act: SignatureAct;
    request?: SignatureCodeRequest;
    outcome?: SignatureActOutcome;
}

type SignatureWay = 'CODE' | 'CIESIGN';

interface HolderForm {
    holderFirstName: string;
    holderLastName: string;
    holderBirthDate?: Date;
    minorBirthPlace: string;
    birthPlace: string;
    residence: string;
    postalCode: string;
    cityProvince: string;
    taxCode: string;
    noTaxCode: boolean;
    document: string;
    documentIssuedBy: string;
    documentIssuedOn?: Date;
    phone: string;
}

const HOLDER_DATA_ERRORS = ['signature.holderData.invalid', 'signature.holderData.birthDateMissing', 'signature.act.acknowledgmentsPending'];
/** Errori del caricamento CieSign mostrati nella finestra, che resta aperta per caricare un altro file o usare il codice. */
const CIESIGN_ERRORS = [
    'signature.cieSign.documentMismatch',
    'signature.cieSign.invalidSignature',
    'signature.cieSign.untrustedCertificate',
    'signature.cieSign.signerMismatch',
    'signature.cieSign.tooLarge',
    'signature.cieSign.notAvailable',
    'signature.cieSign.notConfigured',
    'signature.act.acknowledgmentsPending'
];
/** Dimensione massima del file firmato con CieSign. */
const CIESIGN_MAX_BYTES = 25 * 1024 * 1024;
const TAX_CODE = /^[A-Za-z]{6}[0-9LMNPQRSTUVlmnpqrstuv]{2}[A-Za-z][0-9LMNPQRSTUVlmnpqrstuv]{2}[A-Za-z][0-9LMNPQRSTUVlmnpqrstuv]{3}[A-Za-z]$/;

/**
 * Firma di un atto del foglio di affidamento: per l'emissione l'Affidatario inserisce prima i dati delle sezioni 1 e 2,
 * poi per ogni firma attesa legge il documento e conferma di averlo letto. La firma si conferma con il codice nella
 * finestra successiva oppure, per l'Affidatario con codice fiscale italiano, caricando il documento firmato con CieSign.
 * Il genitore o tutore, dalla pagina pubblica, inserisce anche i propri dati anagrafici e il luogo di nascita del socio
 * minorenne, che prende visione del foglio dal proprio account. La data di nascita del socio viene dal suo profilo, come
 * nome e cognome: finché manca, i dati non si possono inserire e la finestra rimanda al profilo.
 */
@Component({
    selector: 'app-signature-act-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './signature-act-dialog.component.html'
})
export class SignatureActDialogComponent {
    protected act: SignatureAct;
    protected form: HolderForm = {
        holderFirstName: '',
        holderLastName: '',
        minorBirthPlace: '',
        birthPlace: '',
        residence: '',
        postalCode: '',
        cityProvince: '',
        taxCode: '',
        noTaxCode: false,
        document: '',
        documentIssuedBy: '',
        phone: ''
    };
    protected invalidFields = new Set<string>();
    protected read = false;
    protected saving = false;
    protected opening = false;
    protected way: SignatureWay = 'CODE';
    protected cieSignFile?: File;
    protected cieSignError?: string;
    protected downloading = false;
    /** Il server ha rifiutato i dati perché nel profilo manca la data di nascita del socio. */
    private birthDateRefused = false;
    /** Prese visione in attesa secondo il server, che ha rifiutato i dati: prevalgono su quelle note all'apertura. */
    private acknowledgmentsRefused?: number;
    protected readonly today = new Date();
    /** Ultima data di nascita di un genitore o tutore maggiorenne. */
    protected readonly adultBirthDate = new Date(this.today.getFullYear() - 18, this.today.getMonth(), this.today.getDate());
    protected readonly actLabel = signatureActTypeLabel;
    protected readonly documentLabel = signatureDocumentLabel;
    protected readonly purposeLabel = signaturePurposeLabel;
    protected readonly shortHash = shortHash;
    /**
     * Scelte del passo Documento, create una sola volta: un nuovo elenco a ogni controllo delle modifiche fa ricreare i
     * pulsanti del SelectButton all'infinito. Per il genitore, che firma dal link ricevuto via e-mail, CieSign identifica
     * chi firma meglio del codice.
     */
    protected readonly wayOptions: { label: string; value: SignatureWay }[];

    constructor(
        private readonly dialogRef: DynamicDialogRef<SignatureActDialogComponent>,
        private readonly signatureService: SignatureService,
        private readonly apiErrorService: ApiErrorService,
        config: DynamicDialogConfig<SignatureActDialogData>,
        private readonly router: Router
    ) {
        this.act = config.data!.act;
        this.wayOptions = [
            { label: 'Codice via e-mail', value: 'CODE' },
            { label: this.guardian ? 'CieSign (consigliato)' : 'CieSign', value: 'CIESIGN' }
        ];
    }

    protected get dataStep(): boolean {
        return this.act.dataRequired;
    }

    /** L'Affidatario è il genitore o tutore del socio minorenne. */
    protected get guardian(): boolean {
        return this.act.holderRole === 'GUARDIAN';
    }

    /** Il documento riporta la data di nascita del socio, presa dal profilo: senza, i dati non si possono salvare. */
    protected get birthDateMissing(): boolean {
        return this.dataStep && (!this.act.memberBirthDate || this.birthDateRefused);
    }

    /** Consegne del foglio di cui il socio deve ancora prendere visione: fino ad allora l'Affidatario non firma. */
    protected get acknowledgmentsPending(): number {
        const holderStep = this.dataStep || this.purpose === 'ACCEPTANCE' || this.purpose === 'CLAUSE_APPROVAL';
        return holderStep ? (this.acknowledgmentsRefused ?? this.act.pendingAcknowledgments ?? 0) : 0;
    }

    protected get acknowledgmentsHeading(): string {
        if (this.guardian) return 'Il socio deve prima prendere visione delle consegne';
        const count = this.acknowledgmentsPending;
        return count === 1 ? 'Prendi prima visione della consegna in attesa' : `Prendi prima visione delle ${count} consegne in attesa`;
    }

    protected get acknowledgmentsDetail(): string {
        return this.guardian
            ? 'Il socio minorenne conferma dal proprio account di aver preso visione dei dati e delle fotografie dei beni del foglio. Quando lo ha fatto, riapri questo collegamento per firmare.'
            : 'Prima di firmare conferma di aver preso visione dei dati e delle fotografie dei beni del foglio: nell’elenco delle consegne «In possesso» di questa pagina selezionale e scegli «Accetta e firma». Poi torna qui per firmare.';
    }

    /** Manca qualcosa prima della firma: la finestra lo spiega e si chiude, o porta al profilo per la data di nascita. */
    protected get blocked(): boolean {
        return this.birthDateMissing || this.acknowledgmentsPending > 0;
    }

    protected get purpose(): SignaturePurpose | undefined {
        return this.act.pendingPurposes[0];
    }

    protected get document(): SignatureActDocument | undefined {
        const role: SignatureDocumentRole = this.purpose === 'CLAUSE_APPROVAL' ? 'CLAUSES' : 'MAIN';
        return this.act.documents.find((document) => document.role === role);
    }

    protected get heading(): string {
        if (this.dataStep) return 'Dati dell’Affidatario';
        if (this.purpose === 'CLAUSE_APPROVAL') return 'Approvazione delle clausole';
        if (this.purpose === 'ASSOCIATION_SIGNATURE') return 'Firma per l’Associazione';
        if (this.purpose === 'MINOR_ACKNOWLEDGMENT') return 'Presa visione del foglio';
        return 'Firma dell’atto';
    }

    protected get subtitle(): string {
        return `${this.actLabel(this.act.type)} · foglio n. ${this.act.sheetNumber ?? ''} · ${this.act.operation}`;
    }

    protected get confirmLabel(): string {
        if (this.blocked) return this.birthDateMissing && !this.guardian ? 'Vai al profilo' : 'Chiudi';
        if (this.dataStep) return 'Salva e continua';
        return this.cieSign ? 'Carica e firma' : 'Firma con codice';
    }

    /** CieSign si offre all'Affidatario con codice fiscale italiano, per il foglio e per le clausole. */
    protected get cieSignOffered(): boolean {
        return this.act.cieSignAvailable && !this.dataStep && (this.purpose === 'ACCEPTANCE' || this.purpose === 'CLAUSE_APPROVAL');
    }

    protected get cieSign(): boolean {
        return this.cieSignOffered && this.way === 'CIESIGN';
    }

    /** Testo della casella di lettura, non preselezionata. */
    protected get readLabel(): string {
        if (this.purpose === 'CLAUSE_APPROVAL') return 'Approvo specificamente le clausole elencate nel documento';
        if (this.purpose === 'ASSOCIATION_SIGNATURE') return 'Ho letto il documento e firmo per l’Associazione';
        if (this.purpose === 'MINOR_ACKNOWLEDGMENT') return 'Ho letto il foglio e ne prendo visione';
        return 'Ho letto il documento';
    }

    protected get invalidCount(): number {
        if (this.blocked) return 0;
        if (!this.dataStep) return (this.read && this.document ? 0 : 1) + (this.cieSign && !this.validCieSignFile ? 1 : 0);
        return this.missingFields().length;
    }

    private get validCieSignFile(): boolean {
        return !!this.cieSignFile && this.cieSignFile.size > 0 && this.cieSignFile.size <= CIESIGN_MAX_BYTES;
    }

    protected cieSignFileError(attempted: boolean): string | undefined {
        if (this.cieSignFile && this.cieSignFile.size > CIESIGN_MAX_BYTES) return 'Il file firmato supera 25 MB.';
        if (attempted && !this.cieSignFile) return 'Scegli il file firmato con CieSign.';
        return undefined;
    }

    protected changeWay(): void {
        this.cieSignError = undefined;
    }

    protected onCieSignFile(event: Event): void {
        const input = event.target as HTMLInputElement;
        this.cieSignFile = input.files?.[0];
        this.cieSignError = undefined;
    }

    /** Scarica il documento da firmare con CieSign, senza aprirlo, per firmarlo esattamente com'è. */
    protected downloadForCieSign(): void {
        const document = this.document;
        if (!document || this.downloading) return;
        this.downloading = true;
        this.signatureService
            .downloadDocument(this.act.id, document.role)
            .pipe(
                first(),
                finalize(() => (this.downloading = false))
            )
            .subscribe((blob) => {
                const url = URL.createObjectURL(blob);
                const anchor = window.document.createElement('a');
                anchor.href = url;
                anchor.download = `foglio-${(this.act.sheetNumber ?? '').replace('/', '-')}-${document.role === 'CLAUSES' ? 'clausole' : 'foglio'}-da-firmare.pdf`;
                anchor.click();
                URL.revokeObjectURL(url);
            });
    }

    protected missing(field: string): boolean {
        return this.invalidFields.has(field);
    }

    protected fieldError(field: string, attempted: boolean): string | undefined {
        if (!this.invalidFields.has(field) && !(attempted && this.missingFields().includes(field))) return undefined;
        const label = SIGNATURE_HOLDER_FIELDS[field] ?? field;
        if (field === 'taxCode') return 'Inserisci un codice fiscale valido o dichiara di non averne uno italiano.';
        if (field === 'postalCode') return 'Inserisci un CAP di 5 cifre.';
        if (field === 'documentIssuedOn') return 'Inserisci una data di rilascio non futura.';
        if (field === 'holderBirthDate') return 'Inserisci la data di nascita: il genitore o tutore deve essere maggiorenne.';
        return `Inserisci ${label}.`;
    }

    protected openDocument(): void {
        const document = this.document;
        if (!document) return;
        this.opening = true;
        this.signatureService
            .downloadDocument(this.act.id, document.role)
            .pipe(
                first(),
                finalize(() => (this.opening = false))
            )
            .subscribe((blob) => {
                const url = URL.createObjectURL(blob);
                window.open(url, '_blank', 'noopener');
                setTimeout(() => URL.revokeObjectURL(url), 60_000);
            });
    }

    protected confirm(): void {
        if (this.blocked) {
            // Il genitore non ha un profilo: la data la inseriscono l'associazione o il socio.
            const toProfile = this.birthDateMissing && !this.guardian;
            this.cancel();
            if (toProfile) this.router.navigate(['/profile']);
            return;
        }
        if (this.dataStep) {
            this.saveData();
            return;
        }
        const document = this.document;
        if (!this.purpose || !document || !this.read) return;
        if (this.cieSign) {
            this.uploadCieSign(document);
            return;
        }
        this.dialogRef.close({
            act: this.act,
            request: {
                purpose: this.purpose,
                items: [{ actId: this.act.id, documentRole: document.role, documentHash: document.frozenHash }]
            }
        } satisfies SignatureActDialogResult);
    }

    protected cancel(): void {
        this.dialogRef.close({ act: this.act } satisfies SignatureActDialogResult);
    }

    private uploadCieSign(document: SignatureActDocument): void {
        const file = this.cieSignFile;
        if (!file || !this.validCieSignFile) return;
        this.saving = true;
        this.cieSignError = undefined;
        this.signatureService
            .signWithCieSign(this.act.id, document.role, file, withHandledErrors(CIESIGN_ERRORS, withErrorSummary('Firma non registrata')))
            .pipe(
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe({
                next: (outcome) => this.dialogRef.close({ act: this.act, outcome } satisfies SignatureActDialogResult),
                error: (error: HttpErrorResponse) => {
                    const message = this.apiErrorService.handledMessage(error, CIESIGN_ERRORS);
                    if (!message) {
                        this.dialogRef.close({ act: this.act } satisfies SignatureActDialogResult);
                        return;
                    }
                    this.cieSignError = message;
                }
            });
    }

    private saveData(): void {
        if (this.missingFields().length) return;
        this.saving = true;
        this.invalidFields.clear();
        this.signatureService
            .submitHolderData(
                this.act.id,
                {
                    birthPlace: this.form.birthPlace.trim(),
                    residence: this.form.residence.trim(),
                    postalCode: this.form.postalCode.trim(),
                    cityProvince: this.form.cityProvince.trim(),
                    taxCode: this.form.noTaxCode ? undefined : this.form.taxCode.trim().toUpperCase(),
                    noTaxCode: this.form.noTaxCode,
                    document: this.form.document.trim(),
                    documentIssuedBy: this.form.documentIssuedBy.trim(),
                    documentIssuedOn: toLocalDate(this.form.documentIssuedOn)!,
                    phone: this.form.phone.trim() || undefined,
                    ...(this.guardian
                        ? {
                              holderFirstName: this.form.holderFirstName.trim(),
                              holderLastName: this.form.holderLastName.trim(),
                              holderBirthDate: toLocalDate(this.form.holderBirthDate),
                              minorBirthPlace: this.form.minorBirthPlace.trim()
                          }
                        : {})
                },
                withHandledErrors(HOLDER_DATA_ERRORS, withErrorSummary('Dati non salvati'))
            )
            .pipe(
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe({
                next: (act) => {
                    this.act = act;
                    this.read = false;
                },
                error: (error: HttpErrorResponse) => {
                    if (!this.apiErrorService.handledMessage(error, HOLDER_DATA_ERRORS)) {
                        this.dialogRef.close({ act: this.act } satisfies SignatureActDialogResult);
                        return;
                    }
                    const resolved = this.apiErrorService.resolve(error);
                    if (resolved.key === 'signature.holderData.birthDateMissing') {
                        this.birthDateRefused = true;
                        return;
                    }
                    if (resolved.key === 'signature.act.acknowledgmentsPending') {
                        this.acknowledgmentsRefused = Math.max(1, Number(resolved.args['count']) || 1);
                        return;
                    }
                    const fields = resolved.args['fields'];
                    this.invalidFields = new Set(Array.isArray(fields) ? fields.map(String) : []);
                }
            });
    }

    private missingFields(): string[] {
        const missing: string[] = [];
        if (this.guardian) {
            if (!this.form.holderFirstName.trim()) missing.push('holderFirstName');
            if (!this.form.holderLastName.trim()) missing.push('holderLastName');
            if (!this.form.holderBirthDate || this.form.holderBirthDate > this.adultBirthDate) missing.push('holderBirthDate');
        }
        if (!this.form.birthPlace.trim()) missing.push('birthPlace');
        if (!this.form.residence.trim()) missing.push('residence');
        if (!/^\d{5}$/.test(this.form.postalCode.trim())) missing.push('postalCode');
        if (!this.form.cityProvince.trim()) missing.push('cityProvince');
        if (!this.form.noTaxCode && !TAX_CODE.test(this.form.taxCode.trim())) missing.push('taxCode');
        if (!this.form.document.trim()) missing.push('document');
        if (!this.form.documentIssuedBy.trim()) missing.push('documentIssuedBy');
        if (!this.form.documentIssuedOn || this.form.documentIssuedOn > this.today) missing.push('documentIssuedOn');
        if (this.guardian && !this.form.minorBirthPlace.trim()) missing.push('minorBirthPlace');
        return missing;
    }
}
