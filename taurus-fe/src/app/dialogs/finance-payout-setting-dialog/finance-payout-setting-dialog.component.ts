import { Component, DestroyRef, inject, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { catchError, debounceTime, of, Subject, switchMap } from 'rxjs';
import { ImportsModule } from '../../imports';
import { PayoutSettledEvent, PayoutSetting, PayoutSettingRequest } from '../../module';
import { PayoutService, toIsoDate } from '../../service';

export interface PayoutSettingDialogData {
    /** La versione da modificare; assente per una nuova versione. */
    setting?: PayoutSetting;
    /** Valori proposti per una nuova versione, per esempio quelli dell'anteprima di una simulazione. */
    proposal?: { associationSharePercent?: number | null; roundingStep?: number | null };
}

export interface PayoutSettingDialogResult {
    id?: number;
    request: PayoutSettingRequest;
}

/** Passi proposti dal selettore: gli stessi dell'anteprima della simulazione. */
export const PAYOUT_ROUNDING_STEPS = [0, 1, 2, 5, 10, 20];

/**
 * Decorrenza, percentuale di cassa e passo di una versione dei parametri. Prima di salvare dichiara quali eventi già
 * liquidati ricadrebbero nel nuovo calcolo, perché una decorrenza retroattiva cambia le loro quote senza conguagli.
 */
@Component({
    selector: 'app-finance-payout-setting-dialog',
    standalone: true,
    imports: [ImportsModule],
    changeDetection: ChangeDetectionStrategy.Eager,
    templateUrl: './finance-payout-setting-dialog.component.html'
})
export class FinancePayoutSettingDialogComponent implements OnInit {
    protected readonly settingId?: number;
    protected validFrom: Date;
    protected associationSharePercent: number | null;
    protected roundingStep: number | null;
    protected affectedEvents: PayoutSettledEvent[] = [];
    protected affectedLoading = false;
    protected readonly stepOptions = PAYOUT_ROUNDING_STEPS.map((value) => ({ label: value === 0 ? 'Nessuno' : `${value} €`, value }));

    private readonly affected$ = new Subject<void>();
    private readonly destroyRef = inject(DestroyRef);

    constructor(
        private readonly dialogRef: DynamicDialogRef<FinancePayoutSettingDialogComponent>,
        private readonly config: DynamicDialogConfig<any, PayoutSettingDialogData>,
        private readonly payoutService: PayoutService
    ) {
        const setting = this.config.inputValues?.setting;
        const proposal = this.config.inputValues?.proposal;
        this.settingId = setting?.id;
        this.validFrom = setting ? this.parseDate(setting.validFrom) : new Date();
        this.associationSharePercent = setting?.associationSharePercent ?? proposal?.associationSharePercent ?? null;
        this.roundingStep = setting?.roundingStep ?? proposal?.roundingStep ?? 0;
    }

    ngOnInit(): void {
        this.affected$
            .pipe(
                debounceTime(300),
                switchMap(() => {
                    if (!this.validFrom) return of([] as PayoutSettledEvent[]);
                    this.affectedLoading = true;
                    return this.payoutService.getAffectedEvents(toIsoDate(this.validFrom), this.settingId).pipe(catchError(() => of([] as PayoutSettledEvent[])));
                }),
                takeUntilDestroyed(this.destroyRef)
            )
            .subscribe((events) => {
                this.affectedEvents = events;
                this.affectedLoading = false;
            });
        this.affected$.next();
    }

    protected get heading(): string {
        return this.settingId ? 'Modifica versione dei parametri' : 'Nuova versione dei parametri';
    }

    protected get retroactive(): boolean {
        if (!this.validFrom) return false;
        return toIsoDate(this.validFrom) < toIsoDate(new Date());
    }

    protected get validFromError(): string | undefined {
        return this.validFrom ? undefined : 'Indica da quando valgono i parametri.';
    }

    protected get percentError(): string | undefined {
        const value = this.associationSharePercent;
        if (value === null || value === undefined) return 'Indica la percentuale che resta alla cassa.';
        return value < 0 || value > 100 ? 'La percentuale è compresa fra 0 e 100.' : undefined;
    }

    protected get stepError(): string | undefined {
        const value = this.roundingStep;
        return value !== null && value !== undefined && value < 0 ? 'Il passo non può essere negativo.' : undefined;
    }

    protected get invalidCount(): number {
        return (this.validFromError ? 1 : 0) + (this.percentError ? 1 : 0) + (this.stepError ? 1 : 0);
    }

    protected onValidFromChange(): void {
        this.affected$.next();
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    protected save(): void {
        if (this.invalidCount > 0) return;
        const result: PayoutSettingDialogResult = {
            id: this.settingId,
            request: {
                validFrom: toIsoDate(this.validFrom),
                associationSharePercent: this.associationSharePercent ?? 0,
                roundingStep: this.roundingStep ?? 0
            }
        };
        this.dialogRef.close(result);
    }

    private parseDate(value: string): Date {
        const [year, month, day] = value.split('-').map(Number);
        return new Date(year, month - 1, day);
    }
}
