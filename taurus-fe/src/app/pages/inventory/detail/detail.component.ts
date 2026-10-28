import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { concatMap, finalize, first, from } from 'rxjs';
import { InventoryExpirationBadgeComponent } from '../../../components/inventory-expiration-badge/inventory-expiration-badge.component';
import { inventoryAssignmentStatusLabel, inventoryDecisionLabel, inventoryLoanTypeLabel, inventoryLoanTypeOptions, inventoryReturnStatusLabel } from '../../../constants';
import { InventoryIssueListComponent } from '../../../components/inventory-issue-list/inventory-issue-list.component';
import { InventoryLabelDialogComponent } from '../../../dialogs/inventory-label-dialog/inventory-label-dialog.component';
import { InventoryQrRotateDialogComponent } from '../../../dialogs/inventory-qr-rotate-dialog/inventory-qr-rotate-dialog.component';
import { InventoryRenewalDialogComponent } from '../../../dialogs/inventory-renewal-dialog/inventory-renewal-dialog.component';
import { InventorySubstitutionDialogComponent } from '../../../dialogs/inventory-substitution-dialog/inventory-substitution-dialog.component';
import { ImportsModule } from '../../../imports';
import { DetailPageBase } from '../../_shared/detail-page.base';
import {
    InventoryAssignment,
    InventoryAssignmentRequest,
    InventoryCondition,
    InventoryItem,
    InventoryLabelRequest,
    InventoryLoanExpirationRange,
    InventoryLoanType,
    InventoryRenewalRequest,
    InventoryReturn,
    InventorySubstitutionRequest,
    Users,
    UsersCriteria
} from '../../../module';
import { ApiErrorService, ConfirmService, InventoryService, TenantFeatureService, ToastService, UsersService, withErrorSummary, withHandledErrors } from '../../../service';
import { DialogService } from 'primeng/dynamicdialog';
import { focusFragment } from '../../_shared/focus-fragment';

/** Expiration errors shown under the date field instead of a toast: duration rules of the tenant or of the issued sheet. */
const EXPIRATION_ERRORS: readonly string[] = ['inventory.loanDuration.*', 'inventory.loanSheet.missingExpiration', 'inventory.loanSheet.precariousExpiration', 'inventory.loanSheet.expirationOutOfRange'];

@Component({
    selector: 'app-inventory-detail',
    standalone: true,
    imports: [ImportsModule, InventoryExpirationBadgeComponent, InventoryIssueListComponent],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [UsersService, DialogService]
})
export class InventoryDetailComponent extends DetailPageBase implements OnInit {
    protected item: InventoryItem = this.emptyItem();
    protected users: Users[] = [];
    protected selectedUser?: Users;
    protected assignmentQuantity = 1;
    protected assignmentDescription = '';
    protected assignmentExpirationDate?: Date | null;
    protected assignmentAccessories = '';
    /** Scadenze ammesse per la nuova assegnazione di un oggetto a scadenza all'utente scelto. */
    protected assignmentRange?: InventoryLoanExpirationRange;
    /** Scadenze ammesse per le assegnazioni a scadenza non ancora collegate a un foglio. */
    protected assignmentRanges: Partial<Record<number, InventoryLoanExpirationRange>> = {};
    /** Scadenza della nuova assegnazione rifiutata dal backend, con il motivo da mostrare sotto il campo. */
    protected assignmentExpirationProblem?: string;
    /** Scadenze modificate in tabella e rifiutate dal backend, per assegnazione. */
    protected assignmentExpirationProblems: Partial<Record<number, string>> = {};
    protected assignmentEdits: Record<number, InventoryAssignmentRequest> = {};
    protected assignmentExpirationDateEdits: Record<number, Date | null | undefined> = {};
    protected returnQuantities: Record<number, number> = {};
    protected returnRequestNotes: Record<number, string> = {};
    protected returnConditions: Record<number, InventoryCondition | undefined> = {};
    protected returnCompletionNotes: Record<number, string> = {};
    /** Il frammento dell'URL, come `#assegna` dalla scansione, si segue solo al primo caricamento. */
    private fragmentFocused = false;
    protected readonly conditions: { label: string; value: InventoryCondition }[] = [
        { label: 'Nuovo', value: 'NEW' },
        { label: 'Eccellente', value: 'EXCELLENT' },
        { label: 'Buono', value: 'GOOD' },
        { label: 'Discreto', value: 'FAIR' },
        { label: 'Da riparare', value: 'TO_REPAIR' },
        { label: 'Fuori servizio', value: 'OUT_OF_SERVICE' }
    ];

    protected readonly dirtyAssignments = new Set<number>();
    protected readonly inventoryQrEnabled;
    protected readonly assignmentStatusLabel = inventoryAssignmentStatusLabel;
    protected readonly decisionLabel = inventoryDecisionLabel;
    protected readonly returnStatusLabel = inventoryReturnStatusLabel;
    protected readonly loanTypeLabel = inventoryLoanTypeLabel;
    protected readonly loanTypes: { label: string; value: InventoryLoanType | null; description?: string }[] = [{ label: 'Non indicato', value: null }, ...inventoryLoanTypeOptions];

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
        private readonly dialogService: DialogService,
        private readonly apiErrorService: ApiErrorService,
        tenantFeatureService: TenantFeatureService,
        private readonly route: ActivatedRoute,
        private readonly router: Router
    ) {
        super();
        this.inventoryQrEnabled = tenantFeatureService.inventoryQrEnabled;
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

    protected printLabel(): void {
        this.dialogService
            .open(InventoryLabelDialogComponent, {
                header: 'Stampa etichetta',
                showHeader: false,
                modal: true,
                width: '40rem',
                breakpoints: { '767px': 'calc(100vw - 1rem)' },
                data: { items: [this.item] }
            })
            .onClose.pipe(first())
            .subscribe((request?: InventoryLabelRequest) => {
                if (!request) return;
                this.inventoryService
                    .generateLabels(request)
                    .pipe(first())
                    .subscribe((blob) => this.downloadLabels(blob));
            });
    }

    protected rotateQrCode(): void {
        if (!this.item.id) return;
        this.dialogService
            .open(InventoryQrRotateDialogComponent, {
                header: 'Ruota codice QR',
                showHeader: false,
                modal: true,
                width: '28rem',
                breakpoints: { '767px': 'calc(100vw - 1rem)' }
            })
            .onClose.pipe(first())
            .subscribe((reason?: string) => {
                if (!reason) return;
                this.inventoryService
                    .rotateQrCode(this.item.id!, reason)
                    .pipe(first())
                    .subscribe(() => {
                        this.toastService.success('Codice ruotato', 'Stampa una nuova etichetta per rendere nuovamente scansionabile il bene.');
                    });
            });
    }

    protected qrCodeUrl(): string {
        return this.inventoryService.qrCodeUrl(this.item.id!, 256);
    }

    private downloadLabels(blob: Blob): void {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = 'etichette-inventario.pdf';
        anchor.click();
        URL.revokeObjectURL(url);
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

    /** Gli strumenti in comodato con scadenza seguono le regole di durata fin dall'assegnazione. */
    protected get fixedTerm(): boolean {
        return this.item.loanType === 'FIXED_TERM';
    }

    protected rangeHint(range?: InventoryLoanExpirationRange): string | undefined {
        if (!range) return undefined;
        const rules = range.sheetNumber ? `regole del foglio n. ${range.sheetNumber}` : 'regole di durata del comodato';
        return `Tra il ${this.formatDate(range.minimum)} e il ${this.formatDate(range.maximum)} (${rules}).`;
    }

    /** La data in tabella resta libera: il backend la verifica al salvataggio e ne spiega l'eventuale rifiuto. */
    protected editExpiration(assignmentId: number): void {
        this.assignmentExpirationProblems[assignmentId] = undefined;
        this.markAssignmentDirty(assignmentId);
    }

    protected onAssigneeChange(): void {
        this.assignmentRange = undefined;
        this.assignmentExpirationProblem = undefined;
        if (!this.fixedTerm || !this.selectedUser?.id) return;
        const userId = this.selectedUser.id;
        this.inventoryService
            .getLoanExpirationRange(userId)
            .pipe(first())
            .subscribe((range) => {
                if (this.selectedUser?.id === userId) this.assignmentRange = range;
            });
    }

    protected assign(): void {
        if (!this.item.id || !this.selectedUser?.id) return;
        const request: InventoryAssignmentRequest = {
            userIndex: this.selectedUser.id,
            order: this.item.assignments?.length ?? 0,
            quantity: this.assignmentQuantity,
            description: this.assignmentDescription,
            expirationDate: this.toLocalDate(this.assignmentExpirationDate),
            accessories: this.assignmentAccessories.trim() || undefined
        };
        this.assignmentExpirationProblem = undefined;
        this.inventoryService
            .assign(this.item.id, request, withHandledErrors(EXPIRATION_ERRORS, withErrorSummary('Assegnazione non riuscita')))
            .pipe(first())
            .subscribe({
                next: () => {
                    this.toastService.success('Materiale assegnato', 'L’utente può ora prenderne visione dal profilo.');
                    this.selectedUser = undefined;
                    this.assignmentRange = undefined;
                    this.assignmentQuantity = 1;
                    this.assignmentDescription = '';
                    this.assignmentExpirationDate = undefined;
                    this.assignmentAccessories = '';
                    this.loadItem(this.item.id!);
                },
                error: (error: HttpErrorResponse) => (this.assignmentExpirationProblem = this.apiErrorService.handledMessage(error, EXPIRATION_ERRORS))
            });
    }

    protected saveAssignment(assignmentId: number): void {
        const edit = this.assignmentEdits[assignmentId];
        if (!edit || edit.quantity < 1) return;
        const request: InventoryAssignmentRequest = {
            ...edit,
            expirationDate: this.toLocalDate(this.assignmentExpirationDateEdits[assignmentId])
        };
        this.assignmentExpirationProblems[assignmentId] = undefined;
        this.inventoryService
            .updateAssignment(assignmentId, request, withHandledErrors(EXPIRATION_ERRORS, withErrorSummary('Assegnazione non aggiornata')))
            .pipe(first())
            .subscribe({
                next: () => {
                    this.dirtyAssignments.delete(assignmentId);
                    this.syncAssignmentsUnit();
                    this.toastService.success('Assegnazione aggiornata', 'Le modifiche dell’assegnazione sono state salvate.');
                    this.loadItem(this.item.id!);
                },
                error: (error: HttpErrorResponse) => (this.assignmentExpirationProblems[assignmentId] = this.apiErrorService.handledMessage(error, EXPIRATION_ERRORS))
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

    protected canRenew(assignment: InventoryAssignment): boolean {
        return !!assignment.loanSheetId && assignment.loanType === 'FIXED_TERM' && assignment.outstandingQuantity > 0 && !!assignment.expirationDate;
    }

    protected canSubstitute(assignment: InventoryAssignment): boolean {
        return !!assignment.loanSheetId && assignment.outstandingQuantity > 0 && !this.hasOpenReturn(assignment);
    }

    protected renew(assignment: InventoryAssignment): void {
        if (!assignment.loanSheetId) return;
        this.inventoryService
            .getLoanSheet(assignment.loanSheetId)
            .pipe(first())
            .subscribe((sheet) =>
                this.dialogService
                    .open(InventoryRenewalDialogComponent, {
                        header: 'Rinnova comodato',
                        showHeader: false,
                        modal: true,
                        width: '40rem',
                        breakpoints: { '767px': 'calc(100vw - 1rem)' },
                        data: { assignment, terms: sheet.terms }
                    })
                    .onClose.pipe(first())
                    .subscribe((request?: InventoryRenewalRequest) => {
                        if (!request) return;
                        this.inventoryService
                            .renewAssignment(assignment.id, request)
                            .pipe(first())
                            .subscribe(() => {
                                this.toastService.success('Rinnovo registrato', 'Il rinnovo compare nel registro delle variazioni e il socio deve prenderne visione.');
                                this.loadItem(this.item.id!);
                            });
                    })
            );
    }

    protected substitute(assignment: InventoryAssignment): void {
        if (!assignment.loanSheetId) return;
        this.inventoryService
            .getLoanSheet(assignment.loanSheetId)
            .pipe(first())
            .subscribe((sheet) =>
                this.dialogService
                    .open(InventorySubstitutionDialogComponent, {
                        header: 'Sostituisci bene',
                        showHeader: false,
                        modal: true,
                        width: '44rem',
                        breakpoints: { '767px': 'calc(100vw - 1rem)' },
                        data: { assignment, terms: sheet.terms }
                    })
                    .onClose.pipe(first())
                    .subscribe((request?: InventorySubstitutionRequest) => {
                        if (!request) return;
                        this.inventoryService
                            .substituteAssignment(assignment.id, request)
                            .pipe(first())
                            .subscribe((delivered) => {
                                this.toastService.success('Sostituzione registrata', `${delivered.itemName} è stato consegnato al posto di ${assignment.itemName}.`);
                                this.loadItem(this.item.id!);
                            });
                    })
            );
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

    /** Le fotografie scelte insieme si caricano una alla volta, nell'ordine di selezione; al primo errore le restanti non partono. */
    protected upload(event: Event): void {
        const input = event.target as HTMLInputElement;
        const itemId = this.item.id;
        const files = Array.from(input.files ?? []);
        if (!files.length || !itemId) return;
        const photos = this.acceptedPhotos(files);
        if (!photos.length) {
            input.value = '';
            return;
        }
        from(photos)
            .pipe(
                concatMap((file) => this.inventoryService.uploadPhoto(itemId, file)),
                finalize(() => {
                    input.value = '';
                    this.loadItem(itemId);
                })
            )
            .subscribe({ error: () => undefined });
    }

    protected uploadReturnPhoto(value: InventoryReturn, event: Event): void {
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];
        if (!file) return;
        if (!this.acceptedPhotos([file]).length) {
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
                            expirationDate: assignment.expirationDate,
                            accessories: assignment.accessories
                        }
                    ])
                );
                this.assignmentExpirationDateEdits = Object.fromEntries((value.assignments ?? []).map((assignment) => [assignment.id, this.fromLocalDate(assignment.expirationDate)]));
                this.loadAssignmentRanges(value);
                (value.assignments ?? []).forEach((assignment) => {
                    this.returnQuantities[assignment.id] = Math.max(1, assignment.outstandingQuantity);
                    assignment.returns.forEach((itemReturn) => {
                        this.returnConditions[itemReturn.id] = itemReturn.condition;
                        this.returnCompletionNotes[itemReturn.id] = itemReturn.notes ?? '';
                    });
                });
                this.clearDirtyUnits();
                this.dirtyAssignments.clear();
                if (!this.fragmentFocused) {
                    this.fragmentFocused = true;
                    focusFragment(this.route.snapshot.fragment);
                }
            });
    }

    private loadUsers(): void {
        this.usersService
            .getAll({ page: 0, size: 1000, sort: ['name,asc'] } as UsersCriteria)
            .pipe(first())
            .subscribe((page) => (this.users = page.content));
    }

    /** Tiene i file ammessi e segnala in un solo messaggio quelli esclusi. */
    private acceptedPhotos(files: File[]): File[] {
        const accepted = files.filter((file) => file.size <= 10 * 1024 * 1024 && ['image/jpeg', 'image/png'].includes(file.type));
        const rejected = files.filter((file) => !accepted.includes(file)).map((file) => file.name);
        if (rejected.length) {
            this.toastService.error(
                rejected.length === 1 ? 'Fotografia non valida' : 'Fotografie non valide',
                `${rejected.length === 1 ? 'File escluso' : 'File esclusi'}: ${rejected.join(', ')}. Sono ammessi JPEG/PNG fino a 10 MB. WebP non è supportato.`
            );
        }
        return accepted;
    }

    /** Le assegnazioni a scadenza ancora libere da un foglio si correggono entro le regole che si applicherebbero alla consegna. */
    private loadAssignmentRanges(item: InventoryItem): void {
        this.assignmentRanges = {};
        if (item.loanType !== 'FIXED_TERM') return;
        (item.assignments ?? [])
            .filter((assignment) => !assignment.loanSheetId && assignment.outstandingQuantity > 0)
            .forEach((assignment) =>
                this.inventoryService
                    .getLoanExpirationRange(assignment.userIndex, this.toLocalDate(new Date(assignment.assignedAt)))
                    .pipe(first())
                    .subscribe((range) => (this.assignmentRanges[assignment.id] = range))
            );
    }

    private formatDate(value: string): string {
        const [year, month, day] = value.split('-');
        return `${day}/${month}/${year}`;
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
