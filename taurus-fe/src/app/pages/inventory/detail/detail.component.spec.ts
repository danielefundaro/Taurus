import { ActivatedRoute, Router } from '@angular/router';
import { DialogService } from 'primeng/dynamicdialog';
import { of, Subject } from 'rxjs';
import { InventoryItem } from '../../../module';
import { ApiErrorService, ConfirmService, InventoryService, TenantFeatureService, ToastService, UsersService } from '../../../service';
import { InventoryDetailComponent } from './detail.component';

describe('InventoryDetailComponent', () => {
    function create(): {
        component: InventoryDetailComponent;
        inventoryService: jasmine.SpyObj<InventoryService>;
        toastService: jasmine.SpyObj<ToastService>;
        uploads: Subject<unknown>[];
    } {
        const inventoryService = jasmine.createSpyObj<InventoryService>('InventoryService', ['uploadPhoto', 'getItem']);
        const uploads: Subject<unknown>[] = [];
        inventoryService.uploadPhoto.and.callFake(() => {
            const upload = new Subject<unknown>();
            uploads.push(upload);
            return upload;
        });
        const toastService = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);
        const component = new InventoryDetailComponent(
            inventoryService,
            {} as UsersService,
            toastService,
            {} as ConfirmService,
            {} as DialogService,
            {} as ApiErrorService,
            { inventoryQrEnabled: () => false } as unknown as TenantFeatureService,
            {} as ActivatedRoute,
            {} as Router
        );
        const item: InventoryItem = { ...component['item'], id: 7, photos: [], assignments: [] };
        component['item'] = item;
        inventoryService.getItem.and.returnValue(of(item));
        return { component, inventoryService, toastService, uploads };
    }

    function photo(name: string, type = 'image/jpeg'): File {
        return new File(['x'], name, { type });
    }

    function selection(files: File[]): { event: Event; input: HTMLInputElement } {
        const input = document.createElement('input');
        input.type = 'file';
        Object.defineProperty(input, 'files', { value: files });
        return { event: { target: input } as unknown as Event, input };
    }

    function complete(upload: Subject<unknown>): void {
        upload.next({});
        upload.complete();
    }

    it('uploads the chosen photos one at a time in the order of selection and reloads the item once', () => {
        const { component, inventoryService, uploads } = create();
        const first = photo('fronte.jpg');
        const second = photo('retro.png', 'image/png');

        component['upload'](selection([first, second]).event);

        expect(inventoryService.uploadPhoto.calls.allArgs()).toEqual([[7, first]]);
        complete(uploads[0]);
        expect(inventoryService.uploadPhoto.calls.allArgs()).toEqual([
            [7, first],
            [7, second]
        ]);
        expect(inventoryService.getItem).not.toHaveBeenCalled();
        complete(uploads[1]);
        expect(inventoryService.getItem).toHaveBeenCalledOnceWith(7);
    });

    it('leaves out invalid files with a single message and uploads the others', () => {
        const { component, inventoryService, toastService, uploads } = create();
        const valid = photo('fronte.jpg');
        const large = photo('grande.jpg');
        Object.defineProperty(large, 'size', { value: 10 * 1024 * 1024 + 1 });

        component['upload'](selection([photo('custodia.webp', 'image/webp'), valid, large]).event);
        complete(uploads[0]);

        expect(toastService.error).toHaveBeenCalledOnceWith('Fotografie non valide', jasmine.stringContaining('File esclusi: custodia.webp, grande.jpg.'));
        expect(inventoryService.uploadPhoto.calls.allArgs()).toEqual([[7, valid]]);
    });

    it('sends nothing when no chosen file is valid', () => {
        const { component, inventoryService, toastService } = create();

        component['upload'](selection([photo('custodia.webp', 'image/webp')]).event);

        expect(toastService.error).toHaveBeenCalledOnceWith('Fotografia non valida', jasmine.stringContaining('File escluso: custodia.webp.'));
        expect(inventoryService.uploadPhoto).not.toHaveBeenCalled();
        expect(inventoryService.getItem).not.toHaveBeenCalled();
    });

    it('stops at the first refused upload and still shows the photos already added', () => {
        const { component, inventoryService, uploads } = create();
        const { event, input } = selection([photo('uno.jpg'), photo('due.jpg'), photo('tre.jpg')]);
        const valueSetter = spyOnProperty(input, 'value', 'set').and.callThrough();

        component['upload'](event);
        complete(uploads[0]);
        uploads[1].error(new Error('inventory.photo.limit'));

        expect(inventoryService.uploadPhoto).toHaveBeenCalledTimes(2);
        expect(inventoryService.getItem).toHaveBeenCalledOnceWith(7);
        expect(valueSetter).toHaveBeenCalledWith('');
    });
});
