import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { InventoryItem } from '../../module';
import { InventoryLabelDialogComponent } from './inventory-label-dialog.component';

describe('InventoryLabelDialogComponent', () => {
    const items = [
        { id: 1, inventoryNumber: 'LEG-1', name: 'Leggio', totalQuantity: 4, conditionStatus: 'GOOD' },
        { id: 2, inventoryNumber: 'TAM-2', name: 'Tamburo', totalQuantity: 1, conditionStatus: 'GOOD' }
    ] as InventoryItem[];

    function create(values = items) {
        const close = jasmine.createSpy('close');
        const component = new InventoryLabelDialogComponent({ close } as unknown as DynamicDialogRef<InventoryLabelDialogComponent>, { data: { items: values } } as DynamicDialogConfig);
        return { component, close };
    }

    it('starts with one copy of each item on an A4 sheet', () => {
        const { component } = create();

        expect(component['totalLabels']).toBe(2);
        expect(component['invalidCount']).toBe(0);
    });

    it('applies the separate limits of the single label and of the A4 sheet', () => {
        const { component } = create();
        component['copies'][1] = 20;
        component['copies'][2] = 1;
        component['layout'] = 'SINGLE_62X40';
        expect(component['invalidCount']).toBe(1);

        component['layout'] = 'A4_GRID_3X8';
        component['startCell'] = 220;
        expect(component['invalidCount']).toBe(1);

        component['startCell'] = 219;
        expect(component['invalidCount']).toBe(0);
    });

    it('rejects copies outside one to twenty and an empty selection', () => {
        expect(create([])['component']['invalidCount']).toBe(1);
        const { component } = create();
        component['copies'][1] = 0;
        expect(component['invalidCount']).toBe(1);
        component['copies'][1] = 21;
        expect(component['invalidCount']).toBe(1);
    });

    it('sends start cell and cut marks only for the A4 sheet', () => {
        const { component, close } = create();
        component['layout'] = 'SINGLE_62X40';
        component['startCell'] = 5;
        component['showCutMarks'] = true;

        component['save']();

        expect(close).toHaveBeenCalledOnceWith({
            layout: 'SINGLE_62X40',
            startCell: 0,
            showCutMarks: false,
            entries: [
                { itemId: 1, copies: 1 },
                { itemId: 2, copies: 1 }
            ]
        });
    });

    it('does not close with an invalid request', () => {
        const { component, close } = create();
        component['copies'][2] = 0;

        component['save']();

        expect(close).not.toHaveBeenCalled();
    });
});
