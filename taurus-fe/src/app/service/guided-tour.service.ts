import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { computed, Injectable, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { catchError, filter, finalize, Observable, of, shareReplay, switchMap, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { GuidedTour, GuidedTourProgress } from '../module';
import { withErrorSummary, withHandledErrors } from './api-error-context';
import { KeycloakService } from './keycloak.service';
import { TenantFeatureService } from './tenant-feature.service';
import { TenantStatusService } from './tenant-status.service';
import { ToastService } from './toast.service';

/** One opening of the tour: the component composes the path again for each. */
export interface GuidedTourSession {
    id: number;
    presentation: 'tour' | 'invitation';
    /** Opened by itself: closing it counts as an automatic proposal. */
    automatic: boolean;
}

/**
 * State, start rules and saving of the guided tour (docs/guided-tour-spec.md). The catalog and the overlay live in a
 * deferred chunk: this service only knows step identifiers.
 */
@Injectable({ providedIn: 'root' })
export class GuidedTourService {
    static readonly MAX_AUTO_PROPOSALS = 3;
    static readonly SAVE_DELAY_MS = 1000;
    static readonly AUTO_START_DELAY_MS = 600;

    private readonly resourceUrl = `${environment.baseUrl}/guided-tour`;
    /** Reads and writes started by the tour stay silent: an error of the tour must never disturb the page. */
    private readonly silent = withHandledErrors(['*']);
    private readonly state = signal<GuidedTour | null>(null);
    private readonly sessionState = signal<GuidedTourSession | null>(null);
    private readonly openedManually = signal(false);
    private inFlight?: Observable<GuidedTour | null>;
    private sessions = 0;
    private catalogRevision = 0;
    /** Version of the saved progress, `null` while the role has none. */
    private savedVersion: number | null = null;
    private dirty = false;
    private saving = false;
    private saveTimer?: ReturnType<typeof setTimeout>;
    private proposalTimer?: ReturnType<typeof setTimeout>;

    readonly tour = this.state.asReadonly();
    readonly session = this.sessionState.asReadonly();
    readonly active = computed(() => this.sessionState() !== null);
    /** Dot of the help button: the tour no longer opens by itself, but the role has not concluded it. */
    readonly attention = computed(() => {
        const progress = this.state()?.progress;
        return !this.openedManually() && progress?.status === 'IN_PROGRESS' && progress.autoProposals >= GuidedTourService.MAX_AUTO_PROPOSALS;
    });

    constructor(
        private readonly http: HttpClient,
        private readonly router: Router,
        private readonly keycloakService: KeycloakService,
        private readonly tenantStatusService: TenantStatusService,
        private readonly tenantFeatureService: TenantFeatureService,
        private readonly toastService: ToastService
    ) {
        // The invitation is a banner of the dashboard: it leaves with the page.
        this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => {
            if (this.sessionState()?.presentation === 'invitation' && !this.onDashboard()) this.close();
        });
    }

    /** Reads the state once, after the tenant is confirmed provisioned; `null` when the tour cannot start. */
    load(): Observable<GuidedTour | null> {
        const cached = this.state();
        if (cached) return of(cached);
        if (this.inFlight) return this.inFlight;

        this.inFlight = this.tenantStatusService.load().pipe(
            switchMap((status) => (status.provisioned ? this.http.get<GuidedTour>(this.resourceUrl, { context: this.silent }) : of(null))),
            tap((tour) => tour && this.adopt(tour)),
            // Without the state the tour does not start; the help button reads it again when used.
            catchError(() => of(null)),
            finalize(() => (this.inFlight = undefined)),
            shareReplay({ bufferSize: 1, refCount: false })
        );
        return this.inFlight;
    }

    /** Called by the dashboard after its first rendering: opens the tour or the invitation when the rules allow it. */
    proposeFromDashboard(): void {
        clearTimeout(this.proposalTimer);
        this.proposalTimer = setTimeout(
            () =>
                this.load().subscribe((tour) => {
                    if (!tour || this.sessionState() || !this.mayPropose(tour)) return;
                    const progress = tour.progress;
                    if (tour.settings.legacyInvitation && !progress) this.start('invitation', false);
                    else if (!progress || (progress.status === 'IN_PROGRESS' && progress.autoProposals < GuidedTourService.MAX_AUTO_PROPOSALS)) this.start('tour', true);
                }),
            GuidedTourService.AUTO_START_DELAY_MS
        );
    }

    /** Help button and profile: always available, even with the automatic start switched off. */
    open(): void {
        this.openedManually.set(true);
        this.load().subscribe((tour) => {
            if (tour?.enabled) this.start('tour', false);
            else this.toastService.warn('Guida non disponibile', 'Riprova tra qualche istante.');
        });
    }

    useCatalogRevision(revision: number): void {
        this.catalogRevision = revision;
    }

    /** «Inizia la guida» from the welcome. */
    begin(welcomeId: string): void {
        this.change((progress) => ({ status: 'IN_PROGRESS', seenSteps: union(progress.seenSteps, [welcomeId]), lastStepId: welcomeId }), true);
    }

    /** A step shown: saved within a second, together with the steps that follow quickly. */
    visit(stepId: string): void {
        this.change((progress) => ({ seenSteps: union(progress.seenSteps, [stepId]), lastStepId: stepId }), false);
    }

    /** Close or `Esc`: the role keeps its last step. */
    pause(): void {
        const automatic = this.sessionState()?.automatic === true;
        this.close();
        if (!this.state()?.progress) return;
        this.change((progress) => ({ autoProposals: automatic ? Math.min(progress.autoProposals + 1, GuidedTourService.MAX_AUTO_PROPOSALS) : progress.autoProposals }), true);
    }

    skip(): void {
        this.close();
        this.change(() => ({ status: 'SKIPPED' }), true);
        this.toastService.info('Guida chiusa', 'Puoi riaprirla dal pulsante ? in alto.');
    }

    complete(stepId: string): void {
        this.close();
        this.change((progress) => ({ status: 'COMPLETED', seenSteps: union(progress.seenSteps, [stepId]), lastStepId: stepId }), true);
    }

    /** «Più tardi»: no automatic start until the end of the browser session. */
    postpone(): void {
        this.close();
        try {
            sessionStorage.setItem(this.postponeKey(), 'true');
        } catch {
            // Without session storage the tour is proposed again at the next visit of the dashboard.
        }
    }

    /** «Inizia la guida» of the invitation: the welcome opens as if asked from the help button. */
    acceptInvitation(): void {
        this.start('tour', false);
    }

    /** «No, grazie» of the invitation. */
    declineInvitation(): void {
        this.close();
        this.change(() => ({ status: 'SKIPPED' }), true);
    }

    /** «Non avviare più la guida automaticamente» from the welcome. */
    disableAutoStart(): void {
        this.postpone();
        const tour = this.state();
        if (!tour) return;
        this.http.put<GuidedTour>(`${this.resourceUrl}/settings`, { autoStartEnabled: false, version: tour.settings.version }, { context: this.silent }).subscribe({
            next: (saved) => {
                this.adopt(saved);
                this.toastService.info('Avvio automatico disattivato', 'Puoi riattivarlo dal profilo, nella sezione Guida.');
            },
            error: () => undefined
        });
    }

    /** Switch of the profile: an explicit command, so its errors follow the usual handling. */
    saveAutoStart(enabled: boolean): Observable<GuidedTour> {
        const version = this.state()?.settings.version ?? null;
        return this.http
            .put<GuidedTour>(`${this.resourceUrl}/settings`, { autoStartEnabled: enabled, version }, { context: withErrorSummary('Guida non aggiornata') })
            .pipe(tap((saved) => this.adopt(saved)));
    }

    /** «Ricomincia la guida» of the profile: clears the state of the role of the session only. */
    restart(): Observable<GuidedTour> {
        clearTimeout(this.saveTimer);
        this.dirty = false;
        return this.http.delete<GuidedTour>(`${this.resourceUrl}/progress`, { context: withErrorSummary('Guida non ricominciata') }).pipe(tap((saved) => this.adopt(saved)));
    }

    /** Reads the state again, for example after a version conflict of the profile. */
    reload(): void {
        this.http.get<GuidedTour>(this.resourceUrl, { context: this.silent }).subscribe({ next: (tour) => this.adopt(tour), error: () => undefined });
    }

    private start(presentation: GuidedTourSession['presentation'], automatic: boolean): void {
        this.sessionState.set({ id: ++this.sessions, presentation, automatic });
    }

    private close(): void {
        this.sessionState.set(null);
    }

    /** The rules shared by the automatic start and the invitation. */
    private mayPropose(tour: GuidedTour): boolean {
        return (
            tour.enabled &&
            tour.settings.autoStartEnabled &&
            this.onDashboard() &&
            this.tenantFeatureService.loaded() &&
            !this.postponed() &&
            // A dialog, a confirmation or a full-screen tool is open.
            !document.querySelector('.p-dialog-mask, .p-overlay-mask')
        );
    }

    private onDashboard(): boolean {
        return this.router.url.split(/[?#]/)[0] === '/';
    }

    private postponed(): boolean {
        try {
            return sessionStorage.getItem(this.postponeKey()) === 'true';
        } catch {
            return false;
        }
    }

    private postponeKey(): string {
        return `guidedTour.postponed.${this.keycloakService.currentUserId}.${this.keycloakService.currentUserTenantCode}.${this.keycloakService.currentUserRole}`;
    }

    /** Takes the state of the server, keeping the steps taken while a write was travelling. */
    private adopt(tour: GuidedTour): void {
        this.savedVersion = tour.progress?.version ?? null;
        const local = this.state()?.progress;
        this.state.set(this.dirty && local ? { ...tour, progress: local } : tour);
    }

    private change(update: (progress: GuidedTourProgress) => Partial<GuidedTourProgress>, immediate: boolean): void {
        const tour = this.state();
        if (!tour) return;
        const current: GuidedTourProgress = tour.progress ?? { status: 'IN_PROGRESS', seenSteps: [], lastStepId: null, autoProposals: 0, catalogRevision: 0, startedAt: null, completedAt: null, version: 0 };
        const progress = { ...current, ...update(current) };
        progress.catalogRevision = Math.max(progress.catalogRevision, this.catalogRevision);
        this.state.set({ ...tour, progress });
        this.dirty = true;
        clearTimeout(this.saveTimer);
        if (immediate) this.save(true);
        else this.saveTimer = setTimeout(() => this.save(true), GuidedTourService.SAVE_DELAY_MS);
    }

    /**
     * One write at a time. A version conflict, as with two open tabs, reads the state again, merges the seen steps and
     * sends once more; any other error waits for the next step.
     */
    private save(mergeOnConflict: boolean): void {
        clearTimeout(this.saveTimer);
        const progress = this.state()?.progress;
        if (this.saving || !this.dirty || !progress) return;
        this.dirty = false;
        this.saving = true;
        const { status, seenSteps, lastStepId, autoProposals, catalogRevision } = progress;
        this.http.put<GuidedTour>(`${this.resourceUrl}/progress`, { status, seenSteps, lastStepId, autoProposals, catalogRevision, version: this.savedVersion }, { context: this.silent }).subscribe({
            next: (saved) => {
                this.saving = false;
                this.adopt(saved);
                if (this.dirty) this.save(true);
            },
            error: (error: HttpErrorResponse) => {
                this.saving = false;
                this.dirty = true;
                if (mergeOnConflict && error.status === 409) this.merge();
            }
        });
    }

    private merge(): void {
        this.saving = true;
        this.http.get<GuidedTour>(this.resourceUrl, { context: this.silent }).subscribe({
            next: (server) => {
                this.saving = false;
                const local = this.state()?.progress;
                this.savedVersion = server.progress?.version ?? null;
                this.state.set({ ...server, progress: local ? { ...local, seenSteps: union(server.progress?.seenSteps ?? [], local.seenSteps) } : server.progress });
                this.save(false);
            },
            error: () => (this.saving = false)
        });
    }
}

function union(first: readonly string[], second: readonly string[]): string[] {
    return [...new Set([...first, ...second])];
}
