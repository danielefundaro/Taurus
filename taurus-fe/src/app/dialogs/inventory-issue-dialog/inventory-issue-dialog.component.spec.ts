import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { of, Subject, throwError } from 'rxjs';
import { InventoryIssue } from '../../module';
import { InventoryService, ToastService, UserInventoryService } from '../../service';
import { INVENTORY_ISSUE_MAX_PENDING_PHOTOS, InventoryIssueDialogComponent } from './inventory-issue-dialog.component';

describe('InventoryIssueDialogComponent', () => {
    const issue = { id: 900 } as InventoryIssue;
    let objectUrls: number;

    beforeEach(() => {
        objectUrls = 0;
        spyOn(URL, 'createObjectURL').and.callFake(() => `blob:preview-${++objectUrls}`);
        spyOn(URL, 'revokeObjectURL');
    });

    function create(data: { itemId?: number; assignmentId?: number; maxQuantity: number }) {
        const close = jasmine.createSpy('close');
        const inventoryService = jasmine.createSpyObj<InventoryService>('InventoryService', ['createIssue', 'uploadIssuePhoto']);
        const userInventoryService = jasmine.createSpyObj<UserInventoryService>('UserInventoryService', ['createIssue', 'uploadIssuePhoto']);
        const toastService = jasmine.createSpyObj<ToastService>('ToastService', ['error', 'warn']);
        inventoryService.createIssue.and.returnValue(of(issue));
        userInventoryService.createIssue.and.returnValue(of(issue));
        const component = new InventoryIssueDialogComponent({ close } as unknown as DynamicDialogRef<InventoryIssueDialogComponent>, { data } as DynamicDialogConfig, inventoryService, userInventoryService, toastService);
        return { component, close, inventoryService, userInventoryService, toastService };
    }

    function choose(component: InventoryIssueDialogComponent, files: File[]): HTMLInputElement {
        const input = document.createElement('input');
        Object.defineProperty(input, 'files', { value: files });
        component['addPhotos']({ target: input } as unknown as Event);
        return input;
    }

    function photo(name: string, type = 'image/jpeg'): File {
        return new File(['x'], name, { type });
    }

    it('requires a quantity within the limit and a description', () => {
        const { component } = create({ itemId: 42, maxQuantity: 3 });
        expect(component['invalidCount']).toBe(1);

        component['quantity'] = 4;
        component['description'] = 'Asta piegata';
        expect(component['invalidCount']).toBe(1);

        component['quantity'] = 3;
        expect(component['invalidCount']).toBe(0);
    });

    it('previews the valid photos, leaves out the others and stops at the maximum', () => {
        const { component, toastService } = create({ itemId: 42, maxQuantity: 1 });
        const large = photo('grande.jpg');
        Object.defineProperty(large, 'size', { value: 10 * 1024 * 1024 + 1 });

        choose(component, [photo('a.jpg'), photo('b.webp', 'image/webp'), large]);
        expect(component['photos'].map((value) => value.file.name)).toEqual(['a.jpg']);
        expect(toastService.error).toHaveBeenCalledTimes(1);

        choose(
            component,
            Array.from({ length: INVENTORY_ISSUE_MAX_PENDING_PHOTOS }, (_, index) => photo(`p${index}.png`, 'image/png'))
        );
        expect(component['photos'].length).toBe(INVENTORY_ISSUE_MAX_PENDING_PHOTOS);
        expect(toastService.warn).toHaveBeenCalledTimes(1);
    });

    it('removes a photo before sending and releases its preview', () => {
        const { component } = create({ itemId: 42, maxQuantity: 1 });
        choose(component, [photo('a.jpg'), photo('b.jpg')]);

        component['removePhoto'](0);

        expect(component['photos'].map((value) => value.file.name)).toEqual(['b.jpg']);
        expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview-1');
    });

    it('creates the issue without photos and closes with it', () => {
        const { component, close, inventoryService } = create({ itemId: 42, maxQuantity: 2 });
        component['quantity'] = 2;
        component['severity'] = 'UNSAFE';
        component['description'] = '  Asta piegata  ';

        component['save']();

        expect(inventoryService.createIssue).toHaveBeenCalledOnceWith(42, 2, 'UNSAFE', 'Asta piegata');
        expect(close).toHaveBeenCalledOnceWith(issue);
    });

    it('uploads the photos of an own assignment one at a time after the issue', () => {
        const { component, close, userInventoryService, inventoryService } = create({ assignmentId: 7, maxQuantity: 1 });
        const uploads: Subject<unknown>[] = [];
        userInventoryService.uploadIssuePhoto.and.callFake(() => {
            const upload = new Subject<unknown>();
            uploads.push(upload);
            return upload;
        });
        component['description'] = 'Asta piegata';
        const [first, second] = [photo('a.jpg'), photo('b.png', 'image/png')];
        choose(component, [first, second]);

        component['save']();

        expect(inventoryService.createIssue).not.toHaveBeenCalled();
        expect(userInventoryService.uploadIssuePhoto.calls.allArgs()).toEqual([[900, first]]);
        uploads[0].next({});
        uploads[0].complete();
        expect(userInventoryService.uploadIssuePhoto.calls.allArgs()).toEqual([
            [900, first],
            [900, second]
        ]);
        expect(close).not.toHaveBeenCalled();
        uploads[1].next({});
        uploads[1].complete();
        expect(close).toHaveBeenCalledOnceWith(issue);
    });

    it('keeps the issue when a photo upload fails and says how to complete it', () => {
        const { component, close, inventoryService, toastService } = create({ itemId: 42, maxQuantity: 1 });
        inventoryService.uploadIssuePhoto.and.returnValue(throwError(() => new Error('413')));
        component['description'] = 'Asta piegata';
        choose(component, [photo('a.jpg')]);

        component['save']();

        expect(toastService.warn).toHaveBeenCalled();
        expect(close).toHaveBeenCalledOnceWith(issue);
    });

    it('releases every preview when the dialog closes', () => {
        const { component } = create({ itemId: 42, maxQuantity: 1 });
        choose(component, [photo('a.jpg'), photo('b.jpg')]);

        component.ngOnDestroy();

        expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview-1');
        expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:preview-2');
    });
});
