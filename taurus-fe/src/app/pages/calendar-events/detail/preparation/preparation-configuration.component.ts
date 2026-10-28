import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { finalize, first } from 'rxjs';
import { ImportsModule } from '../../../../imports';
import { EventPreparationConfiguration, EventPreparationView, PreparationProfile } from '../../../../module';
import { ConfirmService, EventPreparationService, withHandledErrors } from '../../../../service';
import { isExplicitChoice, isVersionConflict, PREPARATION_PROFILES, profileConfiguration, RequirementKey, VERSION_CONFLICT } from './preparation-labels';

interface Requirement {
    key: RequirementKey;
    label: string;
}

/**
 * The requirements of the preparation, an independent unit of the page. Before the first save the profile proposes
 * the requirements, leaving the explicit choices undecided; afterwards it is informative and changes nothing else.
 */
@Component({
    selector: 'app-preparation-configuration',
    imports: [ImportsModule],
    templateUrl: './preparation-configuration.component.html'
})
export class PreparationConfigurationComponent implements OnChanges {
    @Input({ required: true }) eventId!: number;
    /** The saved configuration; absent until the plan is created. */
    @Input() configuration?: EventPreparationConfiguration;
    @Input() inventoryEnabled = true;
    @Input() financeEnabled = true;
    @Output() readonly saved = new EventEmitter<EventPreparationView>();
    @Output() readonly dirtyChange = new EventEmitter<boolean>();
    /** Another request saved first: the page reloads the preparation. */
    @Output() readonly conflict = new EventEmitter<void>();

    protected draft?: EventPreparationConfiguration;
    protected dirty = false;
    protected saving = false;
    protected readonly profiles = PREPARATION_PROFILES;
    protected readonly choices = [
        { label: 'Richiesto', value: true },
        { label: 'Non richiesto', value: false }
    ];
    protected readonly requirements: Requirement[] = [
        { key: 'locationRequired', label: 'Luogo' },
        { key: 'programRequired', label: 'Programma musicale' },
        { key: 'scoresRequired', label: 'Spartiti' },
        { key: 'availabilityRequired', label: 'Disponibilità' },
        { key: 'materialsRequired', label: 'Materiali' },
        { key: 'budgetRequired', label: 'Preventivo' },
        { key: 'presenceClosureRequired', label: 'Verifica presenze dopo l’evento' },
        { key: 'financialClosureRequired', label: 'Chiusura economica' }
    ];

    constructor(
        private readonly eventPreparationService: EventPreparationService,
        private readonly confirmService: ConfirmService
    ) {}

    ngOnChanges(changes: SimpleChanges): void {
        // A reload caused by another unit never discards the requirements being changed here.
        if (changes['configuration'] && !this.dirty) this.draft = this.configuration ? { ...this.configuration } : undefined;
    }

    protected get configured(): boolean {
        return !!this.configuration;
    }

    protected get undecided(): Requirement[] {
        return this.requirements.filter((requirement) => this.draft?.[requirement.key] === null);
    }

    protected get participantsMissing(): boolean {
        return !!this.draft?.availabilityRequired && !this.draft.minimumAvailableParticipants;
    }

    protected get canSave(): boolean {
        return this.dirty && !!this.draft && !this.saving && this.undecided.length === 0 && !this.participantsMissing;
    }

    protected changeProfile(profile: PreparationProfile): void {
        if (this.draft && this.configured) this.draft = { ...this.draft, profile };
        else this.draft = profileConfiguration(profile, { inventory: this.inventoryEnabled, finance: this.financeEnabled });
        this.markDirty();
    }

    protected setRequirement(key: RequirementKey, value: boolean): void {
        if (!this.draft) return;
        this.draft = { ...this.draft, [key]: value };
        if (key === 'programRequired' && !value) this.draft.scoresRequired = false;
        if (key === 'availabilityRequired' && value && !this.draft.minimumAvailableParticipants) this.draft.minimumAvailableParticipants = 1;
        this.markDirty();
    }

    protected disabledReason(key: RequirementKey): string | undefined {
        if (key === 'scoresRequired' && this.draft?.programRequired !== true) return 'Gli spartiti richiedono il programma.';
        if (key === 'materialsRequired' && !this.inventoryEnabled) return 'Il modulo Inventario è disattivato.';
        if ((key === 'budgetRequired' || key === 'financialClosureRequired') && !this.financeEnabled) return 'Il modulo Economia è disattivato.';
        return undefined;
    }

    protected explicit(key: RequirementKey): boolean {
        return !this.configured && !!this.draft && isExplicitChoice(this.draft.profile, key);
    }

    protected markDirty(): void {
        if (this.dirty) return;
        this.dirty = true;
        this.dirtyChange.emit(true);
    }

    protected save(): void {
        if (!this.canSave || !this.draft) return;
        this.saving = true;
        this.eventPreparationService
            .configure(this.eventId, this.draft, withHandledErrors(VERSION_CONFLICT))
            .pipe(
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe({
                next: (view) => {
                    this.reset();
                    this.draft = view.configuration ? { ...view.configuration } : undefined;
                    this.saved.emit(view);
                },
                error: (error) => {
                    if (!isVersionConflict(error)) return;
                    this.confirmService.confirmReversible({
                        title: 'Requisiti modificati da un’altra persona',
                        consequence: 'Ricarica la preparazione per vedere i requisiti aggiornati: le modifiche fatte qui ai requisiti andranno perse.',
                        actionLabel: 'Ricarica',
                        accept: () => {
                            this.reset();
                            this.conflict.emit();
                        }
                    });
                }
            });
    }

    private reset(): void {
        this.dirty = false;
        this.dirtyChange.emit(false);
    }
}
