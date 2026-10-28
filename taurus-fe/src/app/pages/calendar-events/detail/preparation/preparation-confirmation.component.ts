import { Component, EventEmitter, Input, Output } from '@angular/core';
import { finalize, first, Observable } from 'rxjs';
import { RoleEnums } from '../../../../constants';
import { ImportsModule } from '../../../../imports';
import { EventPreparationConfirmations, EventPreparationView } from '../../../../module';
import { EventPreparationService } from '../../../../service';

export type ConfirmationArea = 'budget' | 'presence' | 'noMovements';

const TEXTS: Record<ConfirmationArea, { heading: string; action: string; description: string }> = {
    budget: { heading: 'Preventivo', action: 'Conferma preventivo', description: 'Attesta compenso, costi e valuta attuali dell’evento.' },
    presence: { heading: 'Registro presenze', action: 'Conferma presenze', description: 'Attesta il registro presenze salvato, anche se vuoto.' },
    noMovements: {
        heading: 'Nessun movimento previsto',
        action: 'Conferma nessun movimento',
        description: 'Chiude la posizione economica di un evento senza preventivo né movimenti.'
    }
};

/**
 * One confirmation of the preparation: it attests the current data and says whether an earlier confirmation still
 * matches them. A later change of the data makes it obsolete without blocking any correction.
 */
@Component({
    selector: 'app-preparation-confirmation',
    imports: [ImportsModule],
    templateUrl: './preparation-confirmation.component.html'
})
export class PreparationConfirmationComponent {
    @Input({ required: true }) eventId!: number;
    @Input({ required: true }) area!: ConfirmationArea;
    @Input() confirmations?: EventPreparationConfirmations;
    /** The treasurer confirms through the finance API. */
    @Input() role: RoleEnums = RoleEnums.ADMIN;
    /** Why the confirmation must wait, for example a register with unsaved changes. */
    @Input() blockedReason?: string;
    @Output() readonly confirmed = new EventEmitter<EventPreparationView>();

    protected reason = '';
    protected confirming = false;
    protected readonly reasonLimit = 500;

    constructor(private readonly eventPreparationService: EventPreparationService) {}

    protected get texts() {
        return TEXTS[this.area];
    }

    protected get confirmedAt(): string | undefined {
        const confirmations = this.confirmations;
        if (!confirmations) return undefined;
        return this.area === 'budget' ? confirmations.budgetAt : this.area === 'presence' ? confirmations.presenceAt : confirmations.noMovementsAt;
    }

    protected get current(): boolean {
        const confirmations = this.confirmations;
        if (!confirmations) return false;
        return this.area === 'budget' ? confirmations.budgetCurrent : this.area === 'presence' ? confirmations.presenceCurrent : confirmations.noMovementsCurrent;
    }

    protected get state(): 'current' | 'obsolete' | 'missing' {
        if (!this.confirmedAt) return 'missing';
        return this.current ? 'current' : 'obsolete';
    }

    protected get reasonTooLong(): boolean {
        return this.reason.trim().length > this.reasonLimit;
    }

    protected get buttonLabel(): string {
        return this.state === 'missing' ? this.texts.action : `${this.texts.action} di nuovo`;
    }

    /** A current confirmation is given again only to change the reason of «nessun movimento». */
    protected get canConfirm(): boolean {
        return !this.blockedReason && !this.reasonTooLong && (this.state !== 'current' || this.area === 'noMovements');
    }

    protected confirm(): void {
        if (!this.canConfirm) return;
        this.confirming = true;
        this.request()
            .pipe(
                first(),
                finalize(() => (this.confirming = false))
            )
            .subscribe((view) => {
                this.reason = '';
                this.confirmed.emit(view);
            });
    }

    private request(): Observable<EventPreparationView> {
        if (this.area === 'budget') return this.eventPreparationService.confirmBudget(this.eventId, this.role);
        if (this.area === 'presence') return this.eventPreparationService.confirmPresence(this.eventId);
        return this.eventPreparationService.confirmNoMovements(this.eventId, this.role, this.reason);
    }
}
