import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { finalize, first } from 'rxjs';
import { inventoryConditionLabel } from '../../../../constants';
import { ImportsModule } from '../../../../imports';
import { EventPreparationMaterial, EventPreparationView, InventoryAssignment, InventoryItem } from '../../../../module';
import { ConfirmService, EventPreparationService, InventoryService, withHandledErrors } from '../../../../service';
import { isVersionConflict, MATERIAL_PROBLEM_LABELS, MAX_PREPARATION_ROWS, VERSION_CONFLICT } from './preparation-labels';

type MaterialRow = EventPreparationMaterial & { key: number };

/**
 * The materials of the occurrence, an independent unit of the page. Each row points to an inventory item and to one
 * of its assignments: the quantity is never a second stock, only what that assignment must cover for the event.
 */
@Component({
    selector: 'app-preparation-materials',
    imports: [ImportsModule],
    templateUrl: './preparation-materials.component.html'
})
export class PreparationMaterialsComponent implements OnChanges {
    @Input({ required: true }) eventId!: number;
    @Input() materials: EventPreparationMaterial[] = [];
    /** The `materialsVersion` read with the view, sent back with the save. */
    @Input() version = 0;
    @Output() readonly saved = new EventEmitter<EventPreparationView>();
    @Output() readonly confirmed = new EventEmitter<EventPreparationView>();
    @Output() readonly dirtyChange = new EventEmitter<boolean>();
    @Output() readonly conflict = new EventEmitter<void>();

    protected rows: MaterialRow[] = [];
    protected dirty = false;
    protected saving = false;
    protected confirming?: number;
    protected readonly maxRows = MAX_PREPARATION_ROWS;
    protected readonly conditionLabel = inventoryConditionLabel;

    protected itemSuggestions: InventoryItem[] = [];
    protected assignments: InventoryAssignment[] = [];
    protected selectedItem?: InventoryItem;
    protected selectedAssignmentId?: number;
    protected quantity = 1;

    private nextKey = 1;

    constructor(
        private readonly eventPreparationService: EventPreparationService,
        private readonly inventoryService: InventoryService,
        private readonly confirmService: ConfirmService
    ) {}

    ngOnChanges(changes: SimpleChanges): void {
        if (!changes['materials']) return;
        if (!this.dirty) {
            this.rows = (this.materials ?? []).map((material) => ({ ...material, problems: material.problems ?? [], key: this.nextKey++ }));
            return;
        }
        // While the list is being changed, a confirmation still refreshes the state of its saved row.
        const saved = new Map((this.materials ?? []).map((material) => [material.id, material]));
        this.rows = this.rows.map((row) => {
            const current = row.id ? saved.get(row.id) : undefined;
            return current ? { ...row, confirmed: current.confirmed, confirmationObsolete: current.confirmationObsolete, ready: current.ready, problems: current.problems ?? [] } : row;
        });
    }

    protected get full(): boolean {
        return this.rows.length >= this.maxRows;
    }

    protected problemLabel(code: string): string {
        return MATERIAL_PROBLEM_LABELS[code] ?? code;
    }

    protected assignmentLabel(assignment: InventoryAssignment): string {
        return `${assignment.userName} ${assignment.userLastName} · ${assignment.outstandingQuantity} in carico`;
    }

    protected searchItems(event: AutoCompleteCompleteEvent): void {
        this.inventoryService
            .getItems(event.query, 0, 25)
            .pipe(first())
            .subscribe((page) => (this.itemSuggestions = page.content));
    }

    protected selectItem(item?: InventoryItem): void {
        this.selectedAssignmentId = undefined;
        this.assignments = [];
        if (!item?.id) return;
        this.inventoryService
            .getItem(item.id)
            .pipe(first())
            .subscribe((detail) => (this.assignments = (detail.assignments ?? []).filter((assignment) => ['ACTIVE', 'PARTIALLY_RETURNED'].includes(assignment.status))));
    }

    protected add(): void {
        const item = this.selectedItem;
        if (!item?.id || this.quantity < 1 || this.full) return;
        const assignment = this.assignments.find((value) => value.id === this.selectedAssignmentId);
        this.rows = [
            ...this.rows,
            {
                key: this.nextKey++,
                id: 0,
                itemId: item.id,
                itemName: item.name,
                inventoryNumber: item.inventoryNumber,
                assignmentId: assignment?.id,
                assignee: assignment ? `${assignment.userName} ${assignment.userLastName}` : undefined,
                requiredQuantity: this.quantity,
                outstandingQuantity: assignment?.outstandingQuantity,
                condition: item.conditionStatus,
                unsafeIssueOpen: false,
                confirmed: false,
                confirmationObsolete: false,
                ready: false,
                problems: []
            }
        ];
        this.selectedItem = undefined;
        this.selectedAssignmentId = undefined;
        this.quantity = 1;
        this.assignments = [];
        this.markDirty();
    }

    protected remove(index: number): void {
        this.rows = this.rows.filter((_, position) => position !== index);
        this.markDirty();
    }

    protected confirm(row: MaterialRow): void {
        if (!row.id) return;
        this.confirming = row.id;
        this.eventPreparationService
            .confirmMaterial(this.eventId, row.id)
            .pipe(
                first(),
                finalize(() => (this.confirming = undefined))
            )
            .subscribe((view) => this.confirmed.emit(view));
    }

    protected markDirty(): void {
        if (this.dirty) return;
        this.dirty = true;
        this.dirtyChange.emit(true);
    }

    protected save(): void {
        this.saving = true;
        const materials = this.rows.map((row) => ({ itemId: row.itemId, assignmentId: row.assignmentId, requiredQuantity: row.requiredQuantity, notes: row.notes || undefined }));
        this.eventPreparationService
            .replaceMaterials(this.eventId, materials, this.version, withHandledErrors(VERSION_CONFLICT))
            .pipe(
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe({
                next: (view) => {
                    this.reset();
                    this.saved.emit(view);
                },
                error: (error) => {
                    if (!isVersionConflict(error)) return;
                    this.confirmService.confirmReversible({
                        title: 'Materiali modificati da un’altra persona',
                        consequence: 'Ricarica la preparazione per vedere i materiali aggiornati: le modifiche fatte qui ai materiali andranno perse.',
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
