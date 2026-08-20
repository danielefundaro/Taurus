import { Component, OnInit } from '@angular/core';
import { first } from 'rxjs';
import { ImportsModule } from '../../imports';
import { InventoryAssignmentRequest, InventoryCondition, InventoryErasureRequest, InventoryItem, Users, UsersCriteria } from '../../module';
import { InventoryService, ToastService, UsersService } from '../../service';

@Component({
    selector: 'app-inventory',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './inventory.component.html',
})
export class InventoryComponent implements OnInit {
    protected items: InventoryItem[] = [];
    protected selected?: InventoryItem;
    protected form: InventoryItem = this.emptyItem();
    protected search = '';
    protected saving = false;
    protected users: Users[] = [];
    protected erasureRequests: InventoryErasureRequest[] = [];
    protected selectedUser?: Users;
    protected assignmentQuantity = 1;
    protected assignmentDescription = '';
    protected assignmentEdits: Record<number, InventoryAssignmentRequest> = {};
    protected readonly conditions: { label: string; value: InventoryCondition }[] = [
        { label: 'Nuovo', value: 'NEW' }, { label: 'Eccellente', value: 'EXCELLENT' },
        { label: 'Buono', value: 'GOOD' }, { label: 'Discreto', value: 'FAIR' },
        { label: 'Da riparare', value: 'TO_REPAIR' }, { label: 'Fuori servizio', value: 'OUT_OF_SERVICE' },
    ];

    constructor(
        private readonly inventoryService: InventoryService,
        private readonly usersService: UsersService,
        private readonly toastService: ToastService,
    ) {}

    ngOnInit(): void {
        this.loadItems();
        this.loadErasureRequests();
        this.usersService.getAll({ page: 0, size: 1000, sort: ['name.keyword,asc'] } as UsersCriteria).pipe(first()).subscribe(page => this.users = page.content);
    }

    protected newItem(): void { this.selected = undefined; this.form = this.emptyItem(); }

    protected selectItem(item: InventoryItem): void {
        if (!item.id) return;
        this.inventoryService.getItem(item.id).pipe(first()).subscribe(value => {
            this.selected = value;
            this.form = { ...value };
            this.assignmentEdits = Object.fromEntries((value.assignments ?? []).map(assignment => [assignment.id, {
                userIndex: assignment.userIndex,
                order: assignment.order,
                quantity: assignment.assignedQuantity,
                description: assignment.description,
            }]));
        });
    }

    protected save(): void {
        if (!this.form.name?.trim() || !this.form.inventoryNumber?.trim()) return;
        this.saving = true;
        const request = this.form.id ? this.inventoryService.updateItem(this.form.id, this.form) : this.inventoryService.createItem(this.form);
        request.pipe(first()).subscribe({
            next: value => {
                this.saving = false;
                this.toastService.success('Inventario aggiornato', 'Le modifiche sono state salvate.');
                this.loadItems(); this.selectItem(value);
            },
            error: () => this.saving = false,
        });
    }

    protected remove(): void {
        if (!this.form.id) return;
        this.inventoryService.deleteItem(this.form.id).pipe(first()).subscribe(() => {
            this.toastService.success('Oggetto eliminato', 'L’oggetto è stato rimosso dall’inventario.');
            this.newItem(); this.loadItems();
        });
    }

    protected assign(): void {
        if (!this.form.id || !this.selectedUser?.id) return;
        const request: InventoryAssignmentRequest = { userIndex: this.selectedUser.id, order: this.form.assignments?.length ?? 0, quantity: this.assignmentQuantity, description: this.assignmentDescription };
        this.inventoryService.assign(this.form.id, request).pipe(first()).subscribe(() => {
            this.toastService.success('Materiale assegnato', 'L’utente può ora prenderne visione dal profilo.');
            this.selectedUser = undefined; this.assignmentQuantity = 1; this.assignmentDescription = '';
            this.selectItem(this.form);
        });
    }

    protected reissue(assignmentId: number): void {
        this.inventoryService.reissue(assignmentId).pipe(first()).subscribe(() => {
            this.toastService.success('Presa visione riemessa', 'È stata creata una nuova revisione.');
            this.selectItem(this.form);
        });
    }

    protected saveAssignment(assignmentId: number): void {
        const request = this.assignmentEdits[assignmentId];
        if (!request || request.quantity < 1) return;
        this.inventoryService.updateAssignment(assignmentId, request).pipe(first()).subscribe(() => {
            this.toastService.success('Assegnazione aggiornata', 'Quantità, ordine e descrizione sono stati salvati.');
            this.selectItem(this.form);
        });
    }

    protected upload(event: Event): void {
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];
        if (!file || !this.form.id) return;
        if (file.size > 10 * 1024 * 1024 || !['image/jpeg', 'image/png'].includes(file.type)) {
            this.toastService.error('Fotografia non valida', 'Sono ammessi JPEG/PNG fino a 10 MB. WebP non è supportato.');
            input.value = ''; return;
        }
        this.inventoryService.uploadPhoto(this.form.id, file).pipe(first()).subscribe(() => { input.value = ''; this.selectItem(this.form); });
    }

    protected removePhoto(id: number): void {
        this.inventoryService.deletePhoto(id).pipe(first()).subscribe(() => this.selectItem(this.form));
    }

    protected photoUrl(id: number): string { return this.inventoryService.photoUrl(id, false); }

    protected completeErasure(request: InventoryErasureRequest): void {
        this.inventoryService.completeErasureRequest(request.id).pipe(first()).subscribe(() => {
            this.toastService.success('Cancellazione completata', 'Lo storico inventario è stato pseudonimizzato.');
            this.loadErasureRequests();
        });
    }

    protected loadItems(): void {
        this.inventoryService.getItems(this.search).pipe(first()).subscribe(page => this.items = page.content);
    }

    private loadErasureRequests(): void {
        this.inventoryService.getErasureRequests().pipe(first()).subscribe(values => this.erasureRequests = values);
    }

    private emptyItem(): InventoryItem {
        return { inventoryNumber: '', name: '', totalQuantity: 0, conditionStatus: 'GOOD', currency: 'EUR' };
    }
}
