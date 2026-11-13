import { Component, computed, effect, ElementRef, HostListener, OnDestroy, signal, untracked, viewChild, ChangeDetectionStrategy } from '@angular/core';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { FocusTrapModule } from 'primeng/focustrap';
import { composeGuidedTour, GUIDED_TOUR_CATALOG_REVISION, GUIDED_TOUR_ROLE_ICONS, GUIDED_TOUR_WELCOME } from '../../constants/guided-tour-steps';
import { RoleLabelsMap } from '../../constants';
import { GuidedTourStep } from '../../module';
import { GuidedTourService, KeycloakService, LayoutService, TenantFeatureService } from '../../service';
import { InlineAlertComponent } from '../inline-alert/inline-alert.component';

interface Box {
    top: number;
    left: number;
    width: number;
    height: number;
}

/**
 * Overlay of the guided tour and banner of the invitation. Mounted once by the layout inside `@defer`, so that it
 * and the catalog stay out of the initial bundle. Anchors that are missing, hidden or off-screen are not errors: the
 * step falls back to the next candidate and finally to the centered card.
 */
@Component({
    selector: 'app-guided-tour',
    standalone: true,
    imports: [ButtonModule, FocusTrapModule, InlineAlertComponent],
    templateUrl: './guided-tour.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './guided-tour.component.scss'
})
export class GuidedTourComponent implements OnDestroy {
    static readonly ANCHOR_TIMEOUT_MS = 2000;
    private static readonly ANCHOR_POLL_MS = 100;
    private static readonly SPOTLIGHT_PADDING = 6;
    private static readonly GAP = 12;
    private static readonly MARGIN = 16;
    /** Below this height, as with a 200% zoom, the card is always centered and scrolls. */
    private static readonly MIN_ANCHORED_HEIGHT = 480;

    protected readonly session;
    protected readonly steps = signal<GuidedTourStep[]>([]);
    protected readonly index = signal(0);
    protected readonly step = computed(() => this.steps()[this.index()]);
    protected readonly welcome = computed(() => this.step()?.id === GUIDED_TOUR_WELCOME);
    protected readonly last = computed(() => this.index() === this.steps().length - 1);
    /** A page guide: no welcome, nothing saved, «Chiudi» in place of «Salta la guida». */
    protected readonly pageGuide = computed(() => this.session()?.presentation === 'page');
    /** «Indietro» from the second step, the welcome not counted. */
    protected readonly canGoBack = computed(() => this.index() > (this.pageGuide() ? 0 : 1));
    protected readonly shown = signal(false);
    protected readonly spotlight = signal<Box | null>(null);
    protected readonly position = signal<{ top: number; left: number } | null>(null);
    protected readonly placed = signal(false);
    protected readonly announcement = signal('');

    private readonly card = viewChild<ElementRef<HTMLElement>>('card');
    private targets: HTMLElement[] = [];
    private ticket = 0;
    private frame = 0;
    private resizeObserver?: ResizeObserver;
    private returnFocus: HTMLElement | null = null;
    private readonly relayout = () => this.scheduleLayout();

    constructor(
        protected readonly tour: GuidedTourService,
        private readonly keycloakService: KeycloakService,
        private readonly tenantFeatureService: TenantFeatureService,
        private readonly layoutService: LayoutService,
        private readonly router: Router
    ) {
        this.session = tour.session;
        tour.useCatalogRevision(GUIDED_TOUR_CATALOG_REVISION);
        effect(() => {
            const session = this.session();
            untracked(() => {
                if (session?.presentation === 'tour') this.open();
                else if (session?.presentation === 'page') this.openPage(session.steps ?? []);
                else this.teardown();
            });
        });
    }

    protected get roleLabel(): string {
        const role = this.tour.tour()?.role;
        return RoleLabelsMap.find((label) => label.code === role)?.name ?? '';
    }

    protected get roleIcon(): string {
        const role = this.tour.tour()?.role;
        return (role && GUIDED_TOUR_ROLE_ICONS[role]) || 'pi pi-compass';
    }

    protected get welcomeTitle(): string {
        const name = this.keycloakService.currentUserFirstName;
        return name ? `Benvenuto in Taurus, ${name}` : 'Benvenuto in Taurus';
    }

    protected get welcomeBody(): string {
        const organization = this.tenantFeatureService.current()?.tenantName?.trim() || 'Taurus';
        return `Sei in ${organization} come ${this.roleLabel}. ${this.step()?.body ?? ''}`;
    }

    protected get invitationDetail(): string {
        return `Una guida ti mostra in due minuti cosa puoi fare come ${this.roleLabel}.`;
    }

    protected get autoStartEnabled(): boolean {
        return this.tour.tour()?.settings.autoStartEnabled === true;
    }

    protected begin(): void {
        this.tour.begin(GUIDED_TOUR_WELCOME);
        this.show(1);
    }

    protected next(): void {
        const step = this.step();
        if (!step) return;
        if (!this.last()) this.show(this.index() + 1);
        else if (this.pageGuide()) this.tour.closePage();
        else this.tour.complete(step.id);
    }

    protected back(): void {
        if (this.canGoBack()) this.show(this.index() - 1);
    }

    @HostListener('document:keydown.escape')
    protected close(): void {
        if (!this.shown()) return;
        if (this.pageGuide()) this.tour.closePage();
        else if (this.welcome()) this.tour.postpone();
        else this.tour.pause();
    }

    ngOnDestroy(): void {
        this.teardown();
    }

    private open(): void {
        const tour = this.tour.tour();
        if (!tour) return;
        if (!this.shown()) this.returnFocus = document.activeElement as HTMLElement | null;
        // Features are read once: a flag changed while the tour is open counts from the next opening.
        const steps = composeGuidedTour(tour.role, (feature) => this.tenantFeatureService.isEnabled(feature));
        this.steps.set(steps);
        const progress = tour.progress;
        const resumeAt = progress?.status === 'IN_PROGRESS' && progress.lastStepId ? steps.findIndex((step) => step.id === progress.lastStepId) : -1;
        this.show(Math.max(resumeAt, 0));
    }

    /**
     * A page guide speaks only of what the page shows: it waits for a page still loading until one of its anchors
     * appears, then keeps the steps with a visible anchor. Evaluated once, when the guide opens.
     */
    private async openPage(candidates: GuidedTourStep[]): Promise<void> {
        if (!this.shown()) this.returnFocus = document.activeElement as HTMLElement | null;
        const ticket = ++this.ticket;
        const anchorsOf = (step: GuidedTourStep) => (this.layoutService.isMobile() ? step.mobileAnchors : step.anchors);
        await this.findTargets(
            candidates.flatMap((step) => anchorsOf(step)),
            ticket
        );
        if (ticket !== this.ticket) return;
        const steps = candidates.filter((step) => anchorsOf(step).some((anchor) => visibleElements(anchor).length > 0));
        if (!steps.length) {
            this.tour.pageGuideUnavailable();
            return;
        }
        this.steps.set(steps);
        this.show(0);
    }

    private async show(index: number): Promise<void> {
        const step = this.steps()[index];
        if (!step) return;
        const ticket = ++this.ticket;
        this.index.set(index);
        this.shown.set(false);
        this.placed.set(false);
        this.unobserve();

        if (step.route && this.router.url.split(/[?#]/)[0] !== step.route) {
            // A page with unsaved changes may refuse to leave: the tour pauses on this step and keeps its progress.
            const navigated = await this.router.navigateByUrl(step.route).catch(() => false);
            if (ticket !== this.ticket) return;
            if (!navigated) {
                this.tour.pause();
                return;
            }
        }

        const targets = await this.findTargets(this.layoutService.isMobile() ? step.mobileAnchors : step.anchors, ticket);
        if (ticket !== this.ticket) return;
        this.targets = targets;
        targets[0]?.scrollIntoView({ block: 'center' });
        if (step.id !== GUIDED_TOUR_WELCOME && !this.pageGuide()) this.tour.visit(step.id);
        this.announcement.set(`Passo ${index + 1} di ${this.steps().length}: ${step.id === GUIDED_TOUR_WELCOME ? this.welcomeTitle : step.title}`);
        this.observe();
        this.shown.set(true);
        this.scheduleLayout();
    }

    /** The visible elements of the first candidate anchor that has some, waiting for a late page at most two seconds. */
    private async findTargets(anchors: readonly string[], ticket: number): Promise<HTMLElement[]> {
        if (!anchors.length) return [];
        const deadline = Date.now() + GuidedTourComponent.ANCHOR_TIMEOUT_MS;
        while (ticket === this.ticket) {
            for (const anchor of anchors) {
                const visible = visibleElements(anchor);
                if (visible.length) return visible;
            }
            if (Date.now() >= deadline) break;
            await new Promise((resolve) => setTimeout(resolve, GuidedTourComponent.ANCHOR_POLL_MS));
        }
        return [];
    }

    private observe(): void {
        window.addEventListener('resize', this.relayout);
        window.addEventListener('scroll', this.relayout, { capture: true, passive: true });
        if (typeof ResizeObserver !== 'undefined') {
            this.resizeObserver = new ResizeObserver(this.relayout);
            this.targets.forEach((target) => this.resizeObserver!.observe(target));
        }
    }

    private unobserve(): void {
        window.removeEventListener('resize', this.relayout);
        window.removeEventListener('scroll', this.relayout, { capture: true });
        this.resizeObserver?.disconnect();
        this.resizeObserver = undefined;
        cancelAnimationFrame(this.frame);
        this.frame = 0;
    }

    /** At most one measurement per frame, whatever scrolls or resizes. */
    private scheduleLayout(): void {
        if (this.frame) return;
        this.frame = requestAnimationFrame(() => {
            this.frame = 0;
            this.layout();
        });
    }

    private layout(): void {
        const card = this.card()?.nativeElement;
        if (!card) return;
        const { GAP: gap, MARGIN: margin, SPOTLIGHT_PADDING: padding } = GuidedTourComponent;
        const viewport = { width: window.innerWidth, height: window.innerHeight };
        const target = this.targets.filter((element) => element.isConnected).map((element) => element.getBoundingClientRect());
        const spot = target.length ? padded(union(target), padding, viewport) : null;
        this.spotlight.set(spot);
        this.position.set(spot && viewport.height >= GuidedTourComponent.MIN_ANCHORED_HEIGHT ? place(spot, card.offsetWidth, card.offsetHeight, viewport, gap, margin, this.layoutService.isMobile()) : null);
        if (this.placed()) return;
        // First layout of the card of this step: it follows its own size and takes the focus on the primary command.
        this.placed.set(true);
        this.resizeObserver?.observe(card);
        card.querySelector<HTMLElement>('[data-tour-primary] button')?.focus();
    }

    private teardown(): void {
        this.ticket++;
        this.unobserve();
        this.targets = [];
        const wasShown = this.shown();
        this.shown.set(false);
        this.placed.set(false);
        this.spotlight.set(null);
        this.position.set(null);
        if (wasShown && this.returnFocus?.isConnected) this.returnFocus.focus();
        this.returnFocus = null;
    }
}

function visibleElements(anchor: string): HTMLElement[] {
    return Array.from(document.querySelectorAll<HTMLElement>(`[data-tour-anchor="${anchor}"]`)).filter(isVisible);
}

function isVisible(element: HTMLElement): boolean {
    const rect = element.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0 && rect.right > 0 && rect.left < window.innerWidth && getComputedStyle(element).visibility !== 'hidden';
}

function union(rects: DOMRect[]): Box {
    const top = Math.min(...rects.map((rect) => rect.top));
    const left = Math.min(...rects.map((rect) => rect.left));
    return { top, left, width: Math.max(...rects.map((rect) => rect.right)) - left, height: Math.max(...rects.map((rect) => rect.bottom)) - top };
}

/** The highlighted box with some room around the anchor, kept inside the window. */
function padded(box: Box, padding: number, viewport: { width: number; height: number }): Box {
    const top = Math.max(box.top - padding, 0);
    const left = Math.max(box.left - padding, 0);
    return { top, left, width: Math.min(box.left + box.width + padding, viewport.width) - left, height: Math.min(box.top + box.height + padding, viewport.height) - top };
}

/**
 * Next to the highlighted box: right, below, above and left in this order, only below or above on small screens.
 * `null` when no side has room, and the card is centered.
 */
export function place(spot: Box, width: number, height: number, viewport: { width: number; height: number }, gap: number, margin: number, compact: boolean): { top: number; left: number } | null {
    const clamp = (value: number, max: number) => Math.min(Math.max(value, margin), Math.max(max, margin));
    const middleTop = clamp(spot.top + spot.height / 2 - height / 2, viewport.height - margin - height);
    const middleLeft = compact ? margin : clamp(spot.left + spot.width / 2 - width / 2, viewport.width - margin - width);
    const below = spot.top + spot.height + gap;
    const above = spot.top - gap - height;
    const sides: Array<[boolean, number, number]> = [
        [!compact && spot.left + spot.width + gap + width <= viewport.width - margin, middleTop, spot.left + spot.width + gap],
        [below + height <= viewport.height - margin, below, middleLeft],
        [above >= margin, above, middleLeft],
        [!compact && spot.left - gap - width >= margin, middleTop, spot.left - gap - width]
    ];
    const side = sides.find(([fits]) => fits);
    return side ? { top: side[1], left: side[2] } : null;
}
