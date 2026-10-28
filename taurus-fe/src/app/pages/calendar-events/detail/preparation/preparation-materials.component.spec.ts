import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { RoleEnums } from '../../../../constants';
import { EventPreparationMaterial, EventPreparationView, InventoryAssignment, InventoryItem, Page } from '../../../../module';
import { ConfirmService, EventPreparationService, InventoryService, KeycloakService } from '../../../../service';
import { PreparationMaterialsComponent } from './preparation-materials.component';

const material = (id: number, overrides: Partial<EventPreparationMaterial> = {}): EventPreparationMaterial => ({
    id,
    itemId: id + 100,
    itemName: `Oggetto ${id}`,
    inventoryNumber: `INV-${id}`,
    assignmentId: id + 200,
    assignee: 'Anna Bianchi',
    requiredQuantity: 1,
    outstandingQuantity: 2,
    condition: 'GOOD',
    unsafeIssueOpen: false,
    confirmed: false,
    confirmationObsolete: false,
    ready: false,
    problems: ['CONFIRMATION_MISSING'],
    ...overrides
});

describe('PreparationMaterialsComponent', () => {
    let fixture: ComponentFixture<PreparationMaterialsComponent>;
    let component: PreparationMaterialsComponent;
    let service: jasmine.SpyObj<EventPreparationService>;
    let inventory: jasmine.SpyObj<InventoryService>;
    let confirm: jasmine.SpyObj<ConfirmService>;
    let dirty: boolean[];

    beforeEach(async () => {
        service = jasmine.createSpyObj<EventPreparationService>('EventPreparationService', ['replaceMaterials', 'confirmMaterial']);
        inventory = jasmine.createSpyObj<InventoryService>('InventoryService', ['getItems', 'getItem']);
        confirm = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['confirmReversible']);
        await TestBed.configureTestingModule({
            imports: [PreparationMaterialsComponent],
            providers: [
                provideRouter([]),
                provideNoopAnimations(),
                { provide: EventPreparationService, useValue: service },
                { provide: InventoryService, useValue: inventory },
                { provide: ConfirmService, useValue: confirm },
                { provide: KeycloakService, useValue: { currentUserRole: RoleEnums.ADMIN } }
            ]
        }).compileComponents();
        fixture = TestBed.createComponent(PreparationMaterialsComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('eventId', 42);
        fixture.componentRef.setInput('version', 2);
        dirty = [];
        component.dirtyChange.subscribe((value) => dirty.push(value));
    });

    const state = () => component as unknown as Record<string, any>;

    function render(materials: EventPreparationMaterial[]): HTMLElement {
        fixture.componentRef.setInput('materials', materials);
        fixture.detectChanges();
        return fixture.nativeElement;
    }

    it('shows inventory number, outstanding quantity, link and the problems of each row in words', () => {
        const element = render([material(1, { problems: ['ASSIGNMENT_INACTIVE', 'UNSAFE_ISSUE', 'CONFIRMATION_OBSOLETE'], confirmationObsolete: true })]);

        expect(element.textContent).toContain('N. INV-1');
        expect(element.textContent).toContain('2 in carico');
        expect(element.querySelector('a[href="/inventory/items/101"]')!.textContent).toContain('Oggetto 1');
        expect(element.textContent).toContain('Assegnazione non più attiva');
        expect(element.textContent).toContain('Guasto non sicuro aperto');
        expect(element.textContent).toContain('Conferma di nuovo');
        expect(state()['problemLabel']('NEW_CODE')).toBe('NEW_CODE');
    });

    it('links a new row to an active assignment of the chosen item', () => {
        inventory.getItems.and.returnValue(of({ content: [{ id: 5, name: 'Leggio' }] } as unknown as Page<InventoryItem>));
        inventory.getItem.and.returnValue(
            of({
                id: 5,
                name: 'Leggio',
                assignments: [
                    { id: 9, status: 'ACTIVE', userName: 'Anna', userLastName: 'Bianchi', outstandingQuantity: 3 },
                    { id: 10, status: 'RETURNED', userName: 'Luca', userLastName: 'Verdi', outstandingQuantity: 0 }
                ]
            } as unknown as InventoryItem)
        );
        render([]);

        state()['searchItems']({ query: 'leg' });
        expect(inventory.getItems).toHaveBeenCalledWith('leg', 0, 25);
        const item = { id: 5, name: 'Leggio', inventoryNumber: 'INV-5', conditionStatus: 'GOOD' } as InventoryItem;
        state()['selectedItem'] = item;
        state()['selectItem'](item);
        expect(state()['assignments'].map((value: InventoryAssignment) => value.id)).toEqual([9]);
        expect(state()['assignmentLabel'](state()['assignments'][0])).toBe('Anna Bianchi · 3 in carico');
        state()['selectedAssignmentId'] = 9;
        state()['quantity'] = 2;

        state()['add']();

        expect(state()['rows'][0]).toEqual(jasmine.objectContaining({ itemId: 5, inventoryNumber: 'INV-5', assignmentId: 9, assignee: 'Anna Bianchi', requiredQuantity: 2, outstandingQuantity: 3 }));
        expect(state()['selectedItem']).toBeUndefined();
        expect(dirty).toEqual([true]);
    });

    it('stops at one hundred rows', () => {
        render(Array.from({ length: 100 }, (_, index) => material(index + 1)));
        state()['selectedItem'] = { id: 5, name: 'Leggio' } as InventoryItem;

        state()['add']();

        expect(state()['rows'].length).toBe(100);
        expect(fixture.nativeElement.textContent).toContain('limite di 100 righe');
    });

    it('saves the list with its version and refreshes a confirmed row even while the list has changes', () => {
        const view = { materials: [] } as unknown as EventPreparationView;
        service.replaceMaterials.and.returnValue(of(view));
        service.confirmMaterial.and.returnValue(of(view));
        const confirmed: EventPreparationView[] = [];
        component.confirmed.subscribe((value) => confirmed.push(value));
        render([material(1), material(2)]);

        state()['confirm'](state()['rows'][0]);
        expect(service.confirmMaterial).toHaveBeenCalledWith(42, 1);
        expect(confirmed).toEqual([view]);

        state()['remove'](1);
        fixture.componentRef.setInput('materials', [material(1, { confirmed: true, ready: true, problems: [] }), material(2)]);
        fixture.detectChanges();
        expect(state()['rows'].length).toBe(1);
        expect(state()['rows'][0].confirmed).toBeTrue();
        expect(fixture.nativeElement.textContent).toContain('Pronto');

        state()['save']();
        expect(service.replaceMaterials).toHaveBeenCalledWith(42, [{ itemId: 101, assignmentId: 201, requiredQuantity: 1, notes: undefined }], 2, jasmine.anything());
        expect(dirty).toEqual([true, false]);
    });

    it('offers to reload after a conflict', () => {
        service.replaceMaterials.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
        let conflicts = 0;
        component.conflict.subscribe(() => conflicts++);
        render([material(1)]);
        state()['remove'](0);

        state()['save']();
        confirm.confirmReversible.calls.mostRecent().args[0].accept();

        expect(conflicts).toBe(1);
        expect(dirty).toEqual([true, false]);
    });
});
