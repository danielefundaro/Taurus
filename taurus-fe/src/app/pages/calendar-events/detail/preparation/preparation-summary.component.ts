import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { Params } from '@angular/router';
import { ImportsModule } from '../../../../imports';
import { EventPreparationConfiguration, EventPreparationEvaluation, EventPreparationIssue, EventPreparationIssueDetail, EventPreparationView, PreparationArea } from '../../../../module';
import { focusFragment } from '../../../_shared/focus-fragment';
import { actionLabel, AREA_LABELS, CLOSURE_LABELS, isAnchor, issueSeverity, PHASE_LABELS, SEVERITY_LABELS, sortIssues, STATUS_LABELS, statusSeverity } from './preparation-labels';

/** Where an issue is fixed: a section of the page or another page, already split into router inputs. */
export interface PreparationLink {
    label: string;
    commands: string[];
    queryParams?: Params;
    fragment?: string;
}

interface IssueView {
    issue: EventPreparationIssue;
    link?: PreparationLink;
    details: { detail: EventPreparationIssueDetail; link?: PreparationLink }[];
    hiddenDetails: number;
}

export function preparationLink(action?: string): PreparationLink | undefined {
    if (!action) return undefined;
    if (isAnchor(action)) return { label: actionLabel(action), commands: [], fragment: action.slice(1) };
    const url = new URL(action, 'http://taurus.local');
    const queryParams: Params = {};
    url.searchParams.forEach((value, key) => (queryParams[key] = value));
    return { label: actionLabel(action), commands: [url.pathname], queryParams: Object.keys(queryParams).length ? queryParams : undefined };
}

/**
 * Status, progress and issues of the preparation. Every issue links the section or the page where it is fixed, as
 * far as the role of the reader allows: anchors keep the reader on the page, so the other units keep their changes.
 */
@Component({
    selector: 'app-preparation-summary',
    imports: [ImportsModule],
    templateUrl: './preparation-summary.component.html'
})
export class PreparationSummaryComponent implements OnChanges {
    @Input({ required: true }) evaluation!: EventPreparationEvaluation;
    @Input() configuration?: EventPreparationConfiguration;
    @Input() availability?: EventPreparationView['availability'];
    @Input() programCount = 0;
    @Input() materialsCount = 0;
    /** Only administrators change the requirements, so only they are told about the areas left out. */
    @Input() administrator = false;

    protected issues: IssueView[] = [];
    protected issuesExpanded = false;
    protected readonly severityLabels = SEVERITY_LABELS;
    protected readonly areaLabels = AREA_LABELS;
    protected readonly issueSeverity = issueSeverity;

    ngOnChanges(changes: SimpleChanges): void {
        if (!changes['evaluation']) return;
        this.issues = sortIssues(this.evaluation.issues ?? []).map((issue) => ({
            issue,
            link: preparationLink(issue.action),
            details: (issue.details ?? []).map((detail) => ({ detail, link: preparationLink(detail.action) })),
            hiddenDetails: Math.max(0, (issue.detailCount ?? 0) - (issue.details ?? []).length)
        }));
        // Blockers are in sight from the start; warnings and suggestions wait for «Mostra criticità».
        if (changes['evaluation'].firstChange) this.issuesExpanded = this.evaluation.blockerCount > 0;
    }

    protected get statusLabel(): string {
        return STATUS_LABELS[this.evaluation.preparationStatus];
    }

    protected get statusSeverity() {
        return statusSeverity(this.evaluation.preparationStatus);
    }

    protected get phaseLabel(): string {
        return PHASE_LABELS[this.evaluation.phase];
    }

    protected get closureLabel(): string {
        return CLOSURE_LABELS[this.evaluation.closureStatus];
    }

    protected get progressText(): string {
        return `${this.evaluation.completionPercent}%, ${this.evaluation.passedChecks} di ${this.evaluation.applicableChecks} verifiche completate`;
    }

    protected get countsText(): string {
        const parts = [];
        if (this.evaluation.blockerCount) parts.push(`${this.evaluation.blockerCount} ${this.evaluation.blockerCount === 1 ? 'bloccante' : 'bloccanti'}`);
        if (this.evaluation.warningCount) parts.push(`${this.evaluation.warningCount} da verificare`);
        if (this.evaluation.infoCount) parts.push(`${this.evaluation.infoCount} ${this.evaluation.infoCount === 1 ? 'suggerimento' : 'suggerimenti'}`);
        return parts.join(' · ');
    }

    protected get hiddenIssues(): number {
        return Math.max(0, (this.evaluation.issueCount ?? 0) - this.issues.length);
    }

    protected get excludedText(): string {
        const areas = (this.evaluation.excludedAreas ?? []).map((area: PreparationArea) => AREA_LABELS[area]);
        if (!areas.length) return '';
        return `${areas.join(', ')}: ${areas.length === 1 ? 'l’area è richiesta dal piano ma il suo modulo è disattivato' : 'le aree sono richieste dal piano ma i loro moduli sono disattivati'} per l’associazione, quindi non ${areas.length === 1 ? 'entra' : 'entrano'} nella valutazione finché il modulo non torna attivo.`;
    }

    protected toggleIssues(): void {
        this.issuesExpanded = !this.issuesExpanded;
        if (this.issuesExpanded) focusFragment('preparation-issues-heading');
    }

    /** Anchors move focus to the section: the router changes only the fragment, so nothing on the page is reloaded. */
    protected follow(link: PreparationLink): void {
        if (link.fragment) focusFragment(link.fragment);
    }
}
