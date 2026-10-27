import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { InventoryQrRotateDialogComponent } from './inventory-qr-rotate-dialog.component';

describe('InventoryQrRotateDialogComponent', () => {
    function create() {
        const close = jasmine.createSpy('close');
        const component = new InventoryQrRotateDialogComponent({ close } as unknown as DynamicDialogRef<InventoryQrRotateDialogComponent>);
        return { component, close };
    }

    it('rotates only with a reason and the explicit confirmation', () => {
        const { component, close } = create();
        component['reason'] = 'Etichetta smarrita';
        component['save']();
        expect(close).not.toHaveBeenCalled();

        component['reason'] = '   ';
        component['confirmed'] = true;
        component['save']();
        expect(close).not.toHaveBeenCalled();

        component['reason'] = '  Etichetta smarrita  ';
        component['save']();
        expect(close).toHaveBeenCalledOnceWith('Etichetta smarrita');
    });

    it('closes without a reason when cancelled', () => {
        const { component, close } = create();

        component['cancel']();

        expect(close).toHaveBeenCalledOnceWith();
    });
});
