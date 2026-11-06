import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { first, Subscription } from 'rxjs';
import { LegalTextComponent } from '../../components/legal-text/legal-text.component';
import { ImportsModule } from '../../imports';
import { LegalDocumentType, PublicLegalDocument, PublicLegalDocumentVersion } from '../../module';
import { legalDocumentPath, LegalService, withHandledErrors } from '../../service';

type PageState = 'loading' | 'ready' | 'unavailable' | 'notFound' | 'error';

const TYPE_LABELS: Record<LegalDocumentType, string> = { TERMS: 'Termini di utilizzo', PRIVACY: 'Informativa privacy' };

/**
 * Pagina pubblica di un documento legale, senza login e senza menu: la versione in vigore (`/legal/terms`) o una versione
 * pubblicata al suo indirizzo permanente (`/legal/terms/1.3`), con le altre versioni e la stampa.
 */
@Component({
    selector: 'app-legal-document',
    standalone: true,
    imports: [ImportsModule, LegalTextComponent],
    templateUrl: './legal-document.component.html',
    styleUrl: './legal-document.component.scss'
})
export class LegalDocumentComponent implements OnInit, OnDestroy {
    protected state: PageState = 'loading';
    protected document?: PublicLegalDocument;
    protected type: LegalDocumentType = 'TERMS';
    protected version?: string;
    private subscription?: Subscription;
    private loaded = false;

    constructor(
        private readonly route: ActivatedRoute,
        private readonly legalService: LegalService,
        private readonly title: Title,
        private readonly meta: Meta
    ) {}

    ngOnInit(): void {
        this.type = this.route.snapshot.data['documentType'] === 'PRIVACY' ? 'PRIVACY' : 'TERMS';
        this.title.setTitle(this.typeLabel);
        // Another version opens in the same page: the address changes, the component stays.
        this.subscription = this.route.paramMap.subscribe((params) => {
            this.version = params.get('version') ?? undefined;
            this.load();
        });
    }

    ngOnDestroy(): void {
        this.subscription?.unsubscribe();
        this.meta.removeTag("name='robots'");
    }

    protected get typeLabel(): string {
        return TYPE_LABELS[this.type];
    }

    /** Indirizzo della versione in vigore. */
    protected get currentPath(): string {
        return legalDocumentPath(this.type);
    }

    protected get activeVersion(): PublicLegalDocumentVersion | undefined {
        return this.document?.versions.find((item) => item.active);
    }

    protected get otherVersions(): PublicLegalDocumentVersion[] {
        return this.document?.versions.filter((item) => item.version !== this.document?.version) ?? [];
    }

    protected versionPath(version: string): string {
        return legalDocumentPath(this.type, version);
    }

    protected load(): void {
        this.state = 'loading';
        this.legalService
            .getPublicDocument(this.type, this.version, withHandledErrors(['*']))
            .pipe(first())
            .subscribe({
                next: (document) => {
                    this.document = document;
                    this.state = 'ready';
                    this.describe(document.title, !document.active);
                    if (this.loaded) globalThis.scrollTo?.(0, 0);
                    this.loaded = true;
                },
                error: (error: HttpErrorResponse) => {
                    this.document = undefined;
                    this.state = error.status === 404 ? (this.version ? 'notFound' : 'unavailable') : 'error';
                    this.describe(this.typeLabel, true);
                }
            });
    }

    protected print(): void {
        globalThis.print?.();
    }

    /** Titolo della scheda; una versione ritirata o un esito senza documento restano fuori dai motori di ricerca. */
    private describe(title: string, hidden: boolean): void {
        this.title.setTitle(title);
        if (hidden) this.meta.updateTag({ name: 'robots', content: 'noindex' });
        else this.meta.removeTag("name='robots'");
    }
}
