import { Component, OnInit } from '@angular/core';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { finalize, first, Observable } from 'rxjs';
import { loanTemplateIssueMessage } from '../../../constants';
import { ImportsModule } from '../../../imports';
import {
    InventoryLoanDurations,
    InventoryLoanTemplateCheck,
    InventoryLoanTemplateFile,
    InventoryLoanTemplateOverview,
    InventoryLoanTemplateVariable,
    InventoryLoanTemplateVariableType,
    InventoryLoanTemplateVersion,
    SignatureRequirements
} from '../../../module';
import { ConfirmService, InventoryService, TenantFeatureService, ToastService, withErrorSummary } from '../../../service';
import { DetailPageBase } from '../../_shared/detail-page.base';

const DEFAULT_FILE_NAME = 'foglio-affidamento-modello-predefinito.html';
const PREVIEW_FAILED = 'Anteprima non disponibile';
const ACTIVATION_FAILED = 'Attivazione non riuscita';

/** Requisiti della firma elettronica che dipendono dall'installazione, non dall'associazione. */
const SIGNATURE_REQUIREMENTS: { key: keyof SignatureRequirements; label: string; ready: string; missing: string }[] = [
    { key: 'mail', label: 'Server di posta', ready: 'I codici vengono inviati via e-mail.', missing: 'L’installazione non ha un server di posta configurato.' },
    { key: 'codes', label: 'Chiave dei codici', ready: 'I codici sono protetti dalla chiave dell’installazione.', missing: 'Manca la chiave segreta con cui l’installazione protegge i codici.' },
    { key: 'seal', label: 'Certificato del sigillo', ready: 'I documenti firmati vengono sigillati dall’installazione.', missing: 'Manca il certificato con cui l’installazione sigilla i documenti firmati.' }
];

@Component({
    selector: 'app-inventory-loan-template',
    standalone: true,
    imports: [ImportsModule, ToggleSwitchModule],
    templateUrl: './loan-template.component.html'
})
export class InventoryLoanTemplateComponent extends DetailPageBase implements OnInit {
    protected overview?: InventoryLoanTemplateOverview;
    protected durations: InventoryLoanDurations = { minMonths: 12, maxMonths: 24, renewalMaxMonths: 24, totalMaxMonths: 36 };
    protected file?: InventoryLoanTemplateFile;
    protected fileSize = 0;
    protected check?: InventoryLoanTemplateCheck;
    protected checking = false;
    /** Valore dell'interruttore della firma, riportato a quello salvato se l'utente annulla o il salvataggio fallisce. */
    protected signatureDraft = false;
    /** Azione in corso, per mostrare l'avanzamento sul pulsante che l'ha avviata. */
    protected busy?: string;
    protected override readonly formUnitLabel = 'regole di durata';
    protected readonly typeLabels: Record<InventoryLoanTemplateVariableType, string> = { TEXT: 'Testo', FLAG: 'Sì/No', LIST: 'Elenco' };
    protected readonly issueMessage = loanTemplateIssueMessage;
    protected readonly durationDescription = 'Usate dai controlli sulle scadenze e nel modello tramite le variabili {{durata.mesi…}}. All’emissione vengono copiate sul foglio: i fogli già emessi mantengono le proprie.';
    protected readonly syntax = [
        { example: '{{associazione.nome}}', meaning: 'Inserisce il valore. I caratteri speciali dell’HTML sono sempre convertiti; un valore assente è vuoto.' },
        { example: '{{#beni}} … {{/beni}}', meaning: 'Con un elenco ripete il contenuto per ogni elemento; dentro si usano i campi con il solo nome, per esempio {{nome}}.' },
        { example: '{{#durata.aScadenza}}X{{/durata.aScadenza}}', meaning: 'Con una variabile Sì/No mostra il contenuto se vale sì; con un testo, se non è vuoto.' },
        { example: '{{^foglio.delibera}}________{{/foglio.delibera}}', meaning: 'Mostra il contenuto se la variabile vale no, è vuota o è un elenco vuoto: utile per stampare una linea da compilare a mano.' },
        { example: '{{! nota }}', meaning: 'Commento, non stampato.' }
    ];
    protected readonly rules = [
        'Un solo file .html in UTF-8, al massimo 2 MB comprese le immagini. HTML5 o XHTML; il CSS va scritto nel file, in <style> o negli attributi style.',
        'Impaginazione con il CSS per la stampa: @page per formato e margini, counter(page) e counter(pages) per la numerazione, position: running(…) con element(…) per intestazioni e piè di pagina ripetuti, page-break-before per i salti pagina. Il modello predefinito è l’esempio di riferimento.',
        'Immagini solo incorporate come data: URI PNG o JPEG; il logo dell’associazione è disponibile come {{associazione.logo}}. Non sono ammessi file o indirizzi esterni, @import, @font-face, script e iframe.',
        'Font disponibili: Helvetica (sans-serif), Times (serif) e Courier (monospace). Simboli ed emoji fuori da questi font sono segnalati e non vengono stampati correttamente.',
        'Non sono ammessi l’inserimento senza conversione ({{{…}}} o {{&…}}), l’inclusione di altri modelli ({{>…}}) e il cambio dei delimitatori.'
    ];

    /** La sezione della firma elettronica compare solo se il tenant ha la funzionalità. */
    protected readonly inventorySignatureEnabled;

    constructor(
        private readonly inventoryService: InventoryService,
        private readonly toastService: ToastService,
        private readonly confirmService: ConfirmService,
        tenantFeatureService: TenantFeatureService
    ) {
        super();
        this.inventorySignatureEnabled = tenantFeatureService.inventorySignatureEnabled;
    }

    ngOnInit(): void {
        this.loading = true;
        this.inventoryService
            .getLoanTemplate()
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe((overview) => this.apply(overview));
    }

    protected get activeLabel(): string {
        const active = this.overview?.active;
        if (!this.overview) return '';
        return active ? `Modello caricato · versione ${active.versionNumber}` : `Modello predefinito di Taurus · ${this.overview.defaultLabel}`;
    }

    protected get blockingCount(): number {
        return this.check?.issues.filter((issue) => issue.blocking).length ?? 0;
    }

    protected typeLabel(type: InventoryLoanTemplateVariableType): string {
        return this.typeLabels[type];
    }

    protected token(variable: InventoryLoanTemplateVariable): string {
        return variable.type === 'TEXT' ? `{{${variable.name}}}` : `{{#${variable.name}}}…{{/${variable.name}}}`;
    }

    protected fieldToken(field: InventoryLoanTemplateVariable): string {
        return `{{${field.name}}}`;
    }

    protected async selectFile(input: HTMLInputElement): Promise<void> {
        const selected = input.files?.[0];
        input.value = '';
        if (!selected || !this.overview) return;
        if (selected.size > this.overview.limits.maxFileBytes) {
            this.toastService.error('File troppo grande', 'Il modello non può superare 2 MB, comprese le immagini incorporate.');
            return;
        }
        this.file = { fileName: selected.name, content: await selected.text() };
        this.fileSize = selected.size;
        this.check = undefined;
        this.checking = true;
        this.inventoryService
            .checkLoanTemplate(this.file)
            .pipe(
                first(),
                finalize(() => (this.checking = false))
            )
            .subscribe((check) => (this.check = check));
    }

    protected clearFile(): void {
        this.file = undefined;
        this.check = undefined;
        this.fileSize = 0;
    }

    protected previewFile(): void {
        if (!this.file || !this.check?.valid) return;
        this.run('preview-file', this.inventoryService.previewLoanTemplate(this.file, withErrorSummary(PREVIEW_FAILED)), (blob) => this.openPdf(blob));
    }

    protected activate(): void {
        const file = this.file;
        if (!file || !this.check?.valid) return;
        this.confirmService.confirmReversible({
            title: 'Attiva il modello',
            consequence: 'I nuovi fogli useranno questo modello. I fogli già emessi mantengono il modello con cui sono stati firmati.',
            actionLabel: 'Attiva modello',
            accept: () =>
                this.run('activate', this.inventoryService.activateLoanTemplate(file, withErrorSummary(ACTIVATION_FAILED)), (overview) => {
                    this.toastService.success('Modello attivato', `La versione ${overview.active?.versionNumber} è in uso per i nuovi fogli.`);
                    this.clearFile();
                    this.apply(overview);
                })
        });
    }

    protected useDefault(): void {
        this.confirmService.confirmReversible({
            title: 'Torna al modello predefinito',
            consequence: 'I nuovi fogli useranno il modello predefinito di Taurus. I modelli caricati restano nello storico e possono essere rimessi in uso.',
            actionLabel: 'Usa il predefinito',
            accept: () =>
                this.run('use-default', this.inventoryService.useDefaultLoanTemplate(), (overview) => {
                    this.toastService.success('Modello predefinito in uso', 'I nuovi fogli useranno il modello predefinito di Taurus.');
                    this.apply(overview);
                })
        });
    }

    protected activateVersion(version: InventoryLoanTemplateVersion): void {
        this.confirmService.confirmReversible({
            title: `Usa la versione ${version.versionNumber}`,
            consequence: 'I nuovi fogli useranno questa versione del modello. I fogli già emessi mantengono il modello con cui sono stati firmati.',
            actionLabel: 'Usa questa versione',
            accept: () =>
                this.run(`activate-${version.id}`, this.inventoryService.activateLoanTemplateVersion(version.id, withErrorSummary(ACTIVATION_FAILED)), (overview) => {
                    this.toastService.success('Modello attivato', `La versione ${version.versionNumber} è in uso per i nuovi fogli.`);
                    this.apply(overview);
                })
        });
    }

    protected get signatureRequirements(): { label: string; detail: string; ready: boolean }[] {
        const requirements = this.overview?.signature.requirements;
        return SIGNATURE_REQUIREMENTS.map((requirement) => {
            const ready = !!requirements?.[requirement.key];
            return { label: requirement.label, detail: ready ? requirement.ready : requirement.missing, ready };
        });
    }

    protected get signatureReady(): boolean {
        return this.signatureRequirements.every((requirement) => requirement.ready);
    }

    /** Attiva o disattiva la firma elettronica delle prese visione dopo la conferma dell'amministratore. */
    protected changeSignature(enabled: boolean): void {
        const overview = this.overview;
        if (!overview || enabled === overview.signature.enabled) return;
        const revert = () => (this.signatureDraft = this.overview?.signature.enabled ?? false);
        this.confirmService.confirmReversible({
            title: enabled ? 'Attiva la firma elettronica' : 'Disattiva la firma elettronica',
            consequence: enabled
                ? 'Da ora gli utenti confermano le prese visione con un codice di 6 cifre inviato al loro indirizzo e-mail. Le scelte già registrate non cambiano.'
                : 'Le prese visione torneranno a essere confermate senza codice. Le firme già registrate restano valide e verificabili.',
            actionLabel: enabled ? 'Attiva' : 'Disattiva',
            accept: () => {
                this.busy = 'signature';
                this.inventoryService
                    .updateLoanTemplateSignature(enabled, overview.settingsVersion, withErrorSummary(enabled ? 'Firma elettronica non attivata' : 'Firma elettronica non disattivata'))
                    .pipe(
                        first(),
                        finalize(() => (this.busy = undefined))
                    )
                    .subscribe({
                        next: (updated) => {
                            this.toastService.success(
                                enabled ? 'Firma elettronica attivata' : 'Firma elettronica disattivata',
                                enabled ? 'Le prese visione si confermano con un codice, via e-mail o dall’app di autenticazione.' : 'Le prese visione si confermano senza codice.'
                            );
                            this.apply(updated);
                        },
                        error: revert
                    });
            },
            reject: revert
        });
    }

    protected saveDurations(): void {
        if (!this.overview || !this.isDirty) return;
        this.saving = true;
        this.inventoryService
            .updateLoanDurations(this.durations, this.overview.settingsVersion)
            .pipe(
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe((overview) => {
                this.toastService.success('Regole di durata salvate', 'Valgono per i fogli emessi da ora in poi.');
                this.isDirty = false;
                this.apply(overview);
            });
    }

    protected previewActive(): void {
        const active = this.overview?.active;
        const request = active ? this.inventoryService.previewLoanTemplateVersion(active.id, withErrorSummary(PREVIEW_FAILED)) : this.inventoryService.previewDefaultLoanTemplate(withErrorSummary(PREVIEW_FAILED));
        this.run('preview-active', request, (blob) => this.openPdf(blob));
    }

    protected downloadActive(): void {
        const active = this.overview?.active;
        if (active) this.downloadVersion(active, 'download-active');
        else this.downloadDefault('download-active');
    }

    protected previewDefault(): void {
        this.run('preview-default', this.inventoryService.previewDefaultLoanTemplate(withErrorSummary(PREVIEW_FAILED)), (blob) => this.openPdf(blob));
    }

    protected downloadDefault(key = 'download-default'): void {
        this.run(key, this.inventoryService.downloadDefaultLoanTemplate(), (blob) => this.save(blob, DEFAULT_FILE_NAME));
    }

    protected previewVersion(version: InventoryLoanTemplateVersion): void {
        this.run(`preview-${version.id}`, this.inventoryService.previewLoanTemplateVersion(version.id, withErrorSummary(PREVIEW_FAILED)), (blob) => this.openPdf(blob));
    }

    protected downloadVersion(version: InventoryLoanTemplateVersion, key = `download-${version.id}`): void {
        this.run(key, this.inventoryService.downloadLoanTemplateVersion(version.id), (blob) => this.save(blob, version.fileName));
    }

    private apply(overview: InventoryLoanTemplateOverview): void {
        this.overview = overview;
        this.signatureDraft = overview.signature.enabled;
        if (!this.isDirty) this.durations = { ...overview.durations };
    }

    private run<T>(key: string, request: Observable<T>, next: (value: T) => void): void {
        if (this.busy) return;
        this.busy = key;
        request
            .pipe(
                first(),
                finalize(() => (this.busy = undefined))
            )
            .subscribe(next);
    }

    private openPdf(blob: Blob): void {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank', 'noopener');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
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
