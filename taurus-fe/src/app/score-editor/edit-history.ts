/** Cronologia delle modifiche della sessione di correzione, come istantanee del documento. */
export class EditHistory {
    private readonly undoStack: string[] = [];
    private readonly redoStack: string[] = [];

    constructor(private readonly limit = 100) {}

    get canUndo(): boolean {
        return this.undoStack.length > 0;
    }

    get canRedo(): boolean {
        return this.redoStack.length > 0;
    }

    /** Registra lo stato precedente a una modifica; una nuova modifica annulla la possibilità di ripristinare. */
    record(before: string): void {
        this.undoStack.push(before);
        if (this.undoStack.length > this.limit) this.undoStack.shift();
        this.redoStack.length = 0;
    }

    /** Stato da ripristinare annullando l'ultima modifica, oppure `null`. */
    undo(current: string): string | null {
        const previous = this.undoStack.pop();
        if (previous === undefined) return null;
        this.redoStack.push(current);
        return previous;
    }

    redo(current: string): string | null {
        const next = this.redoStack.pop();
        if (next === undefined) return null;
        this.undoStack.push(current);
        return next;
    }
}
