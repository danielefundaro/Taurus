import { HasUnsavedChanges } from '../../guard/unsaved-changes.guard';

/**
 * Guscio comune delle pagine di dettaglio.
 *
 * Il modulo principale è un'unità salvabile e i template lo marcano come
 * sporco assegnando `isDirty = true`. Ogni sezione che ha un proprio pulsante
 * di salvataggio si registra come unità a parte con {@link setUnitDirty}:
 * `isDirty` resta la somma di tutte le unità — alimenta l'etichetta di stato
 * e la guardia di uscita — mentre `isDirtyForm` è ciò a cui si lega il
 * pulsante dell'intestazione, che salva il solo modulo principale.
 */
export abstract class DetailPageBase implements HasUnsavedChanges {
    loading = false;
    saving = false;

    /** Etichetta con cui il modulo principale compare nella guardia di uscita. */
    protected readonly formUnitLabel: string = 'modulo';

    private formDirty = false;
    private readonly dirtyUnits = new Set<string>();

    /** Vero quando il solo modulo principale ha modifiche non salvate. */
    get isDirtyForm(): boolean {
        return this.formDirty;
    }

    get isDirty(): boolean {
        return this.formDirty || this.dirtyUnits.size > 0;
    }

    set isDirty(value: boolean) {
        this.formDirty = value;
    }

    /** Le unità modificate, nell'ordine in cui vanno elencate all'uscita. */
    get dirtyUnitLabels(): string[] {
        return [...(this.formDirty ? [this.formUnitLabel] : []), ...this.dirtyUnits];
    }

    /** Dichiara lo stato di un'unità salvabile diversa dal modulo principale. */
    protected setUnitDirty(label: string, dirty: boolean): void {
        if (dirty) this.dirtyUnits.add(label);
        else this.dirtyUnits.delete(label);
    }

    protected isUnitDirty(label: string): boolean {
        return this.dirtyUnits.has(label);
    }

    /** Azzera ogni unità: si usa dopo un'eliminazione o un ricaricamento completo. */
    protected clearDirtyUnits(): void {
        this.formDirty = false;
        this.dirtyUnits.clear();
    }
}
