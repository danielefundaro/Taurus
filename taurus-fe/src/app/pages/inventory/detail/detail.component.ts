import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize, first } from 'rxjs';
import { InventoryExpirationBadgeComponent } from '../../../components/inventory-expiration-badge/inventory-expiration-badge.component';
import { ImportsModule } from '../../../imports';
import { DetailPageBase } from '../../_shared/detail-page.base';
import { InventoryAssignment, InventoryAssignmentRequest, InventoryCondition, InventoryItem, InventoryReturn, Users, UsersCriteria } from '../../../module';
import { ConfirmService, InventoryService, ToastService, UsersService } from '../../../service';

@Component({
    selector: 'app-inventory-detail',
    standalone: true,
    imports: [ImportsModule, InventoryExpirationBadgeComponent],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [UsersService]
})
export class InventoryDetailComponent extends DetailPageBase implements OnInit {
    protected item: InventoryItem = this.emptyItem();
    protected users: Users[] = [];
    protected selectedUser?: Users;
    protected assignmentQuantity = 1;
    protected assignmentDescription = '';
    protected assignmentExpirationDate?: Date | null;
    protected assignmentEdits: Record<number, InventoryAssignmentRequest> = {};
    protected assignmentExpirationDateEdits: Record<number, Date | null | undefined> = {};
    protected returnQuantities: Record<number, number> = {};
    protected returnRequestNotes: Record<number, string> = {};
    protected returnConditions: Record<number, InventoryCondition | undefined> = {};
    protected returnCompletionNotes: Record<number, string> = {};
    protected readonly conditions: { label: string; value: InventoryCondition }[] = [
        { label: 'Nuovo', value: 'NEW' },
        { label: 'Eccellente', value: 'EXCELLENT' },
        { label: 'Buono', value: 'GOOD' },
        { label: 'Discreto', value: 'FAIR' },
        { label: 'Da riparare', value: 'TO_REPAIR' },
        { label: 'Fuori servizio', value: 'OUT_OF_SERVICE' }
    ];

    protected readonly dirtyAssignments = new Set<number>();

    private static readonly PHOTO_ORDER_UNIT = 'ordine fotografie';
    private static readonly ASSIGNMENTS_UNIT = 'assegnazioni';

    /** Il modulo principale dell'oggetto: coincide con l'unità del guscio. */
    protected get itemDirty(): boolean {
        return this.isDirtyForm;
    }

    /** L'ordine delle fotografie si salva per conto proprio. */
    protected get photoOrderDirty(): boolean {
        return this.isUnitDirty(InventoryDetailComponent.PHOTO_ORDER_UNIT);
    }

    protected set photoOrderDirty(value: boolean) {
        this.setUnitDirty(InventoryDetailComponent.PHOTO_ORDER_UNIT, value);
    }

    constructor(
        private readonly inventoryService: InventoryService,
        private readonly usersService: UsersService,
        private readonly toastService: ToastService,
        private readonly confirmService: ConfirmService,
        private readonly route: ActivatedRoute,
        private readonly router: Router
    ) {
        super();
    }

    private syncAssignmentsUnit(): void {
        this.setUnitDirty(InventoryDetailComponent.ASSIGNMENTS_UNIT, this.dirtyAssignments.size > 0);
    }

    protected get isNew(): boolean {
        return !this.item.id;
    }

    protected get canSave(): boolean {
        return this.isDirtyForm && !!this.item.name?.trim() && !!this.item.inventoryNumber?.trim();
    }

    ngOnInit(): void {
        this.loadUsers();
        this.route.params.pipe(first()).subscribe((params) => {
            const id = params['id'];
            if (id === 'new') {
                this.item = this.emptyItem();
                this.isDirty = false;
                return;
            }
            const numericId = Number(id);
            if (!Number.isInteger(numericId) || numericId < 1) {
                this.router.navigate(['/inventory']);
                return;
            }
            this.loadItem(numericId);
        });
    }

    protected markItemDirty(): void {
        this.isDirty = true;
    }

    protected markAssignmentDirty(id: number): void {
        this.dirtyAssignments.add(id);
        this.syncAssignmentsUnit();
    }

    protected save(): void {
        if (!this.canSave) return;
        this.saving = true;
        const request = this.item.id ? this.inventoryService.updateItem(this.item.id, this.item) : this.inventoryService.createItem(this.item);
        request
            .pipe(
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe((value) => {
                this.isDirty = false;
                this.toastService.success('Inventario aggiornato', 'Le modifiche sono state salvate.');
                if (this.isNew) {
                    this.router.navigate(['/inventory', value.id]);
                } else {
                    this.loadItem(value.id!);
                }
            });
    }

    protected confirmDelete(): void {
        if (!this.item.id) return;
        this.confirmService.confirmDestructive({
            title: 'Elimina oggetto',
            consequence: `“${this.item.name}” verrà rimosso dall’inventario.`,
            actionLabel: 'Elimina',
            accept: () =>
                this.inventoryService
                    .deleteItem(this.item.id!)
                    .pipe(first())
                    .subscribe(() => {
                        this.clearDirtyUnits();
                        this.dirtyAssignments.clear();
                        this.toastService.success('Oggetto eliminato', 'L’oggetto è stato rimosso dall’inventario.');
                        this.router.navigate(['/inventory']);
                    })
        });
    }

    protected assign(): void {
        if (!this.item.id || !this.selectedUser?.id) return;
        const request: InventoryAssignmentRequest = {
            userIndex: this.selectedUser.id,
            order: this.item.assignments?.length ?? 0,
            quantity: this.assignmentQuantity,
            description: this.assignmentDescription,
            expirationDate: this.toLocalDate(this.assignmentExpirationDate)
        };
        this.inventoryService
            .assign(this.item.id, request)
            .pipe(first())
            .subscribe(() => {
                this.toastService.success('Materiale assegnato', 'L’utente può ora prenderne visione dal profilo.');
                this.selectedUser = undefined;
                this.assignmentQuantity = 1;
                this.assignmentDescription = '';
                this.assignmentExpirationDate = undefined;
                this.loadItem(this.item.id!);
            });
    }

    protected saveAssignment(assignmentId: number): void {
        const edit = this.assignmentEdits[assignmentId];
        if (!edit || edit.quantity < 1) return;
        const request: InventoryAssignmentRequest = {
            ...edit,
            expirationDate: this.toLocalDate(this.assignmentExpirationDateEdits[assignmentId])
        };
        this.inventoryService
            .updateAssignment(assignmentId, request)
            .pipe(first())
            .subscribe(() => {
                this.dirtyAssignments.delete(assignmentId);
                this.syncAssignmentsUnit();
                this.toastService.success('Assegnazione aggiornata', 'Quantità, ordine e descrizione sono stati salvati.');
                this.loadItem(this.item.id!);
            });
    }

    protected confirmDeleteAssignment(assignment: InventoryAssignment): void {
        this.confirmService.confirmDestructive({
            title: 'Elimina assegnazione',
            consequence: `L’assegnazione a ${assignment.userName} ${assignment.userLastName} e tutte le relative riconsegne verranno eliminate.`,
            actionLabel: 'Elimina',
            accept: () =>
                this.inventoryService
                    .deleteAssignment(assignment.id)
                    .pipe(first())
                    .subscribe(() => {
                        this.dirtyAssignments.delete(assignment.id);
                        this.syncAssignmentsUnit();
                        this.toastService.success('Assegnazione eliminata', 'L’assegnazione e le relative riconsegne sono state eliminate.');
                        this.loadItem(this.item.id!);
                    })
        });
    }

    protected confirmDeleteReturn(assignment: InventoryAssignment, itemReturn: InventoryReturn): void {
        this.confirmService.confirmDestructive({
            title: 'Elimina riconsegna',
            consequence: `La procedura di riconsegna di ${itemReturn.quantity} unità per ${assignment.userName} ${assignment.userLastName} verrà eliminata.`,
            actionLabel: 'Elimina',
            accept: () =>
                this.inventoryService
                    .deleteReturn(itemReturn.id)
                    .pipe(first())
                    .subscribe(() => {
                        this.toastService.success('Riconsegna eliminata', 'La procedura di riconsegna è stata eliminata.');
                        this.loadItem(this.item.id!);
                    })
        });
    }

    protected reissue(assignmentId: number): void {
        this.inventoryService
            .reissue(assignmentId)
            .pipe(first())
            .subscribe(() => {
                this.toastService.success('Presa visione riemessa', 'È stata creata una nuova revisione.');
                this.loadItem(this.item.id!);
            });
    }

    protected requestReturn(assignment: InventoryAssignment): void {
        const quantity = this.returnQuantities[assignment.id] || assignment.outstandingQuantity;
        this.inventoryService
            .requestReturn(assignment.id, quantity, this.returnRequestNotes[assignment.id])
            .pipe(first())
            .subscribe(() => {
                this.toastService.success('Riconsegna avviata', 'La procedura di riconsegna è stata aperta.');
                this.loadItem(this.item.id!);
            });
    }

    protected hasOpenReturn(assignment: InventoryAssignment): boolean {
        return assignment.returns.some((value) => value.status === 'REQUESTED');
    }

    protected completeReturn(value: InventoryReturn): void {
        this.inventoryService
            .completeReturn(value.id, value.quantity, this.returnConditions[value.id], this.returnCompletionNotes[value.id])
            .pipe(first())
            .subscribe(() => {
                this.toastService.success('Riconsegna completata', 'Il materiale è stato segnato come riconsegnato.');
                this.loadItem(this.item.id!);
            });
    }

    protected upload(event: Event): void {
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];
        if (!file || !this.item.id) return;
        if (!this.isValidPhoto(file)) {
            input.value = '';
            return;
        }
        this.inventoryService
            .uploadPhoto(this.item.id, file)
            .pipe(first())
            .subscribe(() => {
                input.value = '';
                this.loadItem(this.item.id!);
            });
    }

    protected uploadReturnPhoto(value: InventoryReturn, event: Event): void {
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];
        if (!file) return;
        if (!this.isValidPhoto(file)) {
            input.value = '';
            return;
        }
        this.inventoryService
            .uploadReturnPhoto(value.id, file)
            .pipe(first())
            .subscribe(() => {
                input.value = '';
                this.loadItem(this.item.id!);
            });
    }

    protected removePhoto(id: number): void {
        this.inventoryService
            .deletePhoto(id)
            .pipe(first())
            .subscribe(() => this.loadItem(this.item.id!));
    }

    protected movePhoto(index: number, offset: number): void {
        const photos = [...(this.item.photos ?? [])];
        const destination = index + offset;
        if (destination < 0 || destination >= photos.length) return;
        const [photo] = photos.splice(index, 1);
        photos.splice(destination, 0, photo);
        this.item.photos = photos;
        this.photoOrderDirty = true;
    }

    protected savePhotoOrder(): void {
        if (!this.item.id || !this.photoOrderDirty || !this.item.photos?.length) return;
        this.inventoryService
            .reorderPhotos(
                this.item.id,
                this.item.photos.map((photo) => photo.id)
            )
            .pipe(first())
            .subscribe((photos) => {
                this.item.photos = photos;
                this.photoOrderDirty = false;
                this.toastService.success('Ordine aggiornato', 'Il nuovo ordine delle fotografie è stato salvato.');
            });
    }

    protected setPreviewPhoto(photoId: number): void {
        if (!this.item.id || this.photoOrderDirty) return;
        this.inventoryService
            .setPreviewPhoto(this.item.id, photoId)
            .pipe(first())
            .subscribe((photos) => {
                this.item.photos = photos;
                this.toastService.success('Anteprima aggiornata', 'La fotografia selezionata verrà usata come anteprima.');
            });
    }

    protected photoUrl(id: number): string {
        return this.inventoryService.photoUrl(id);
    }

    protected returnPhotoUrl(id: number): string {
        return this.inventoryService.photoUrl(id).replace('/photos/', '/return-photos/');
    }

    private loadItem(id: number): void {
        this.loading = true;
        this.inventoryService
            .getItem(id)
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe((value) => {
                this.item = value;
                this.assignmentEdits = Object.fromEntries(
                    (value.assignments ?? []).map((assignment) => [
                        assignment.id,
                        {
                            userIndex: assignment.userIndex,
                            order: assignment.order,
                            quantity: assignment.assignedQuantity,
                            description: assignment.description,
                            expirationDate: assignment.expirationDate
                        }
                    ])
                );
                this.assignmentExpirationDateEdits = Object.fromEntries((value.assignments ?? []).map((assignment) => [assignment.id, this.fromLocalDate(assignment.expirationDate)]));
                (value.assignments ?? []).forEach((assignment) => {
                    this.returnQuantities[assignment.id] = Math.max(1, assignment.outstandingQuantity);
                    assignment.returns.forEach((itemReturn) => {
                        this.returnConditions[itemReturn.id] = itemReturn.condition;
                        this.returnCompletionNotes[itemReturn.id] = itemReturn.notes ?? '';
                    });
                });
                this.clearDirtyUnits();
                this.dirtyAssignments.clear();
            });
    }

    private loadUsers(): void {
        this.usersService
            .getAll({ page: 0, size: 1000, sort: ['name,asc'] } as UsersCriteria)
            .pipe(first())
            .subscribe((page) => (this.users = page.content));
    }

    private isValidPhoto(file: File): boolean {
        const valid = file.size <= 10 * 1024 * 1024 && ['image/jpeg', 'image/png'].includes(file.type);
        if (!valid) {
            this.toastService.error('Fotografia non valida', 'Sono ammessi JPEG/PNG fino a 10 MB. WebP non è supportato.');
        }
        return valid;
    }

    private toLocalDate(value?: Date | null): string | undefined {
        if (!value) return undefined;
        const year = value.getFullYear();
        const month = String(value.getMonth() + 1).padStart(2, '0');
        const day = String(value.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    }

    private fromLocalDate(value?: string): Date | undefined {
        if (!value) return undefined;
        const [year, month, day] = value.split('-').map(Number);
        return new Date(year, month - 1, day);
    }

    private emptyItem(): InventoryItem {
        return {
            inventoryNumber: '',
            name: '',
            totalQuantity: 0,
            assignedQuantity: 0,
            availableQuantity: 0,
            conditionStatus: 'GOOD',
            currency: 'EUR',
            photos: [],
            assignments: []
        };
    }
}
