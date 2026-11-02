import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { finalize, first } from 'rxjs';
import { ImportsModule } from '../../../../imports';
import { InstrumentSuggestionAction, InstrumentSuggestionDecision, InstrumentSuggestionName, InstrumentSuggestionPart, Instruments, SheetsMusic, Tracks } from '../../../../module';
import { ApiErrorService, ToastService, TracksService, withErrorSummary, withHandledErrors } from '../../../../service';

/** The choice on a name in the panel; `LATER` leaves the name open. */
export type InstrumentSuggestionChoice = InstrumentSuggestionAction | 'LATER';

/** A name of the panel with the choice being prepared. */
export interface InstrumentSuggestionDraft {
    name: InstrumentSuggestionName;
    choice: InstrumentSuggestionChoice;
    createName: string;
    instrumentId: number | null;
    selected: Set<number>;
}

/** An instrument the name can be associated with: the candidates first, then the other instruments of the tenant. */
export interface InstrumentOption {
    id: number;
    name: string;
    usersCount: number;
    candidate: boolean;
}

/** Errors the panel shows by itself: the track or the instruments changed, so it reads the names again. */
export const INSTRUMENT_SUGGESTION_HANDLED_ERRORS = ['version.conflict', 'instrument.name.exists', 'instrumentSuggestion.notOpen'] as const;

/**
 * The instruments to add of a track: the names read on its parts that match no instrument of the tenant, or more than
 * one. For each name the archivist creates the instrument, associates an existing one or ignores the name; the selected
 * parts receive the instrument in one operation.
 */
@Component({
    selector: 'app-instrument-suggestions-panel',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './instrument-suggestions-panel.component.html',
    styleUrl: './instrument-suggestions-panel.component.scss'
})
export class InstrumentSuggestionsPanelComponent implements OnChanges {
    @Input({ required: true }) trackId!: number;
    /** A new version of the track (a save, a PDF, an application) may have changed the open names. */
    @Input() trackVersion?: number;
    @Input() scores: SheetsMusic[] = [];
    @Input() instruments: Instruments[] = [];
    /** The workspace has unsaved changes: the application would be refused, so the panel waits. */
    @Input() disabled = false;
    @Output() applied = new EventEmitter<Tracks>();
    /** The track changed on the server: the page reads it again. */
    @Output() trackStale = new EventEmitter<void>();
    /** The parts with open names, for the label in the workspace. */
    @Output() openPartsChange = new EventEmitter<number[]>();

    protected drafts: InstrumentSuggestionDraft[] = [];
    protected loading = false;
    protected applying = false;
    protected errorDetail?: string;
    private suggestionsVersion?: number;

    protected readonly choices: { label: string; value: InstrumentSuggestionChoice }[] = [
        { label: 'Crea', value: 'CREATE' },
        { label: 'Associa', value: 'ASSOCIATE' },
        { label: 'Ignora', value: 'IGNORE' },
        { label: 'Più tardi', value: 'LATER' }
    ];

    constructor(
        private readonly tracksService: TracksService,
        private readonly apiErrors: ApiErrorService,
        private readonly toastService: ToastService
    ) {}

    ngOnChanges(changes: SimpleChanges): void {
        if ((changes['trackId'] || changes['trackVersion']) && this.trackId) this.reload();
    }

    protected get hasNames(): boolean {
        return this.drafts.length > 0;
    }

    protected get decided(): InstrumentSuggestionDraft[] {
        return this.drafts.filter((draft) => draft.choice !== 'LATER');
    }

    /** A name to create needs its name, a name to associate the instrument; at least one name must be decided. */
    protected get canApply(): boolean {
        return !this.disabled && !this.applying && this.decided.length > 0 && this.decided.every((draft) => this.isComplete(draft));
    }

    protected isComplete(draft: InstrumentSuggestionDraft): boolean {
        if (draft.choice === 'CREATE') return draft.createName.trim().length > 0;
        if (draft.choice === 'ASSOCIATE') return draft.instrumentId !== null;
        return true;
    }

    protected options(draft: InstrumentSuggestionDraft): InstrumentOption[] {
        const candidates = draft.name.candidates.map((candidate) => ({ ...candidate, candidate: true }));
        const candidateIds = new Set(candidates.map((candidate) => candidate.id));
        const others = this.instruments
            .filter((instrument) => instrument.id !== undefined && !candidateIds.has(instrument.id))
            .map((instrument) => ({ id: instrument.id!, name: instrument.name ?? '', usersCount: instrument.usersCount ?? 0, candidate: false }));
        return [...candidates, ...others];
    }

    /** The instrument will be new, or nobody plays it yet: the parts it receives stay hidden from the members. */
    protected withoutMembers(draft: InstrumentSuggestionDraft): boolean {
        if (draft.choice === 'CREATE') return true;
        if (draft.choice !== 'ASSOCIATE' || draft.instrumentId === null) return false;
        return this.options(draft).find((option) => option.id === draft.instrumentId)?.usersCount === 0;
    }

    protected partTitle(part: InstrumentSuggestionPart): string {
        const index = this.scores.findIndex((score) => score.id === part.sheetMusicId);
        if (index < 0) return 'Parte';
        const score = this.scores[index];
        return `${index + 1}. ${score.description?.trim() || score.instruments?.[0]?.name || 'Parte senza nome'}`;
    }

    protected togglePart(draft: InstrumentSuggestionDraft, part: InstrumentSuggestionPart, selected: boolean): void {
        const next = new Set(draft.selected);
        if (selected) next.add(part.sheetMusicId);
        else next.delete(part.sheetMusicId);
        draft.selected = next;
    }

    protected apply(): void {
        if (!this.canApply || this.suggestionsVersion === undefined) return;
        this.applying = true;
        this.errorDetail = undefined;
        const decisions = this.decided.map((draft) => this.decision(draft));
        this.tracksService
            .applyInstrumentSuggestions(this.trackId, { trackVersion: this.suggestionsVersion, decisions }, withHandledErrors(INSTRUMENT_SUGGESTION_HANDLED_ERRORS, withErrorSummary('Strumenti non applicati')))
            .pipe(
                first(),
                finalize(() => (this.applying = false))
            )
            .subscribe({
                next: (track) => {
                    this.toastService.success('Strumenti applicati', this.outcome(decisions));
                    this.applied.emit(track);
                },
                error: (error: HttpErrorResponse) => this.onApplyError(error)
            });
    }

    private onApplyError(error: HttpErrorResponse): void {
        const handled = this.apiErrors.handledMessage(error, INSTRUMENT_SUGGESTION_HANDLED_ERRORS);
        if (handled === undefined) return;
        this.errorDetail = `${handled}. Il pannello è stato aggiornato: controlla le scelte e riprova.`;
        if (this.apiErrors.key(error) === 'version.conflict') this.trackStale.emit();
        else this.reload(true);
    }

    private decision(draft: InstrumentSuggestionDraft): InstrumentSuggestionDecision {
        const action = draft.choice as InstrumentSuggestionAction;
        if (action === 'IGNORE') return { normalizedName: draft.name.normalizedName, action };
        const sheetMusicIds = draft.name.parts.map((part) => part.sheetMusicId).filter((id) => draft.selected.has(id));
        if (action === 'CREATE') return { normalizedName: draft.name.normalizedName, action, name: draft.createName.trim(), sheetMusicIds };
        return { normalizedName: draft.name.normalizedName, action, instrumentId: draft.instrumentId!, sheetMusicIds };
    }

    private outcome(decisions: InstrumentSuggestionDecision[]): string {
        const created = decisions.filter((decision) => decision.action === 'CREATE').length;
        const associated = decisions.filter((decision) => decision.action === 'ASSOCIATE').length;
        const ignored = decisions.filter((decision) => decision.action === 'IGNORE').length;
        const parts = [
            created ? `${created} ${created === 1 ? 'strumento creato' : 'strumenti creati'}` : undefined,
            associated ? `${associated} ${associated === 1 ? 'nome associato' : 'nomi associati'}` : undefined,
            ignored ? `${ignored} ${ignored === 1 ? 'nome ignorato' : 'nomi ignorati'}` : undefined
        ].filter((part) => part !== undefined);
        return `${parts.join(', ')}. Le parti che hanno ricevuto uno strumento sono da verificare.`;
    }

    /** Reads the open names; `keepError` keeps the message of the refused application visible. */
    private reload(keepError = false): void {
        this.loading = true;
        if (!keepError) this.errorDetail = undefined;
        this.tracksService
            .getInstrumentSuggestions(this.trackId)
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe({
                next: (suggestions) => {
                    this.suggestionsVersion = suggestions.trackVersion;
                    this.drafts = suggestions.names.map((name) => InstrumentSuggestionsPanelComponent.draft(name));
                    this.openPartsChange.emit([...new Set(suggestions.names.flatMap((name) => name.parts.map((part) => part.sheetMusicId)))]);
                },
                error: () => {
                    this.drafts = [];
                    this.openPartsChange.emit([]);
                }
            });
    }

    /**
     * The proposal of the backend, with the parts still without instruments preselected and none of a probable score
     * (decisions 7 and 10 of the specification).
     */
    static draft(name: InstrumentSuggestionName): InstrumentSuggestionDraft {
        const selected = new Set(name.parts.filter((part) => !part.hasInstruments && !part.possibleScore).map((part) => part.sheetMusicId));
        return {
            name,
            choice: name.action,
            createName: name.proposedName,
            instrumentId: name.candidates.length === 1 ? name.candidates[0].id : null,
            selected
        };
    }
}
