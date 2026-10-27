import { HttpErrorResponse } from '@angular/common/http';
import { DynamicDialogConfig } from 'primeng/dynamicdialog';
import { throwError } from 'rxjs';
import { TracksService } from '../../service';
import { ImageEditorDialogComponent } from './image-editor-dialog.component';

describe('ImageEditorDialogComponent', () => {
    function createComponent(): ImageEditorDialogComponent {
        return new ImageEditorDialogComponent({ data: { trackVersion: 1 } } as DynamicDialogConfig, null!, null!, null!, null!);
    }

    /** Un servizio che risponde sempre con l'errore indicato e tiene il conto delle chiavi usate. */
    function createFailingComponent(status: () => number): { component: ImageEditorDialogComponent; keys: string[] } {
        const keys: string[] = [];
        const tracksService = {
            editPage: (_trackId: number, _scoreId: number, _mediaId: number, _recipe: unknown, key: string) => {
                keys.push(key);
                return throwError(() => new HttpErrorResponse({ status: status() }));
            }
        } as unknown as TracksService;
        const component = new ImageEditorDialogComponent(
            { data: { trackId: 1, trackVersion: 1, scoreId: 2, media: { index: 3 } } } as DynamicDialogConfig,
            null!,
            tracksService,
            null!,
            null!
        );
        component['recipe'].grayscale = true;
        return { component, keys };
    }

    it('positions slider values across their configured range', () => {
        const component = createComponent();

        expect(component['sliderPosition'](-100, -100, 100)).toBe(0);
        expect(component['sliderPosition'](0, -100, 100)).toBe(50);
        expect(component['sliderPosition'](100, -100, 100)).toBe(100);
        expect(component['sliderPosition'](300, 0, 255)).toBe(100);
        expect(component['sliderPosition'](180, 0, 360)).toBe(50);
        expect(component['sliderPosition'](null, 0, 255)).toBe(0);
    });

    it('normalizes negative deskew suggestions to the zero-to-360 range', () => {
        const component = createComponent();

        expect(component['normalizeAngle'](-0.8)).toBeCloseTo(359.2);
        expect(component['normalizeAngle'](360)).toBe(0);
        expect(component['isZeroAngle'](360)).toBeTrue();
    });

    it('creates two ordered crop zones with the split presets', () => {
        const component = createComponent();

        component['splitCrop']('vertical');

        expect(component['recipe'].crops).toEqual([
            { x: 0, y: 0, width: 0.5, height: 1 },
            { x: 0.5, y: 0, width: 0.5, height: 1 }
        ]);
        expect(component['activeCropIndex']).toBe(0);

        component['splitCrop']('horizontal');

        expect(component['recipe'].crops).toEqual([
            { x: 0, y: 0, width: 1, height: 0.5 },
            { x: 0, y: 0.5, width: 1, height: 0.5 }
        ]);
    });

    it('discards an unfinished crop when pointer input is cancelled', () => {
        const component = createComponent();
        component['cropStart'] = { x: 0.1, y: 0.2 };

        component['cancelCropDrawing']();

        expect(component['cropStart']).toBeUndefined();
        expect(component['recipe'].crops).toEqual([]);
    });

    it('resizes crop edges and corners while enforcing the minimum size', () => {
        const component = createComponent();
        const crop = { x: 0.1, y: 0.2, width: 0.6, height: 0.5 };

        const expanded = component['resizeCrop'](crop, 'se', { x: 0.9, y: 0.95 });
        expect(expanded).toEqual({ x: 0.1, y: 0.2, width: 0.8, height: 0.75 });

        const minimum = component['resizeCrop'](crop, 'nw', { x: 0.95, y: 0.9 });
        expect(minimum.x).toBeCloseTo(0.68);
        expect(minimum.y).toBeCloseTo(0.68);
        expect(minimum.width).toBeCloseTo(0.02);
        expect(minimum.height).toBeCloseTo(0.02);

        const bounded = component['resizeCrop'](crop, 'e', { x: 1.2, y: 0.4 });
        expect(bounded.x).toBe(0.1);
        expect(bounded.y).toBe(0.2);
        expect(bounded.width).toBeCloseTo(0.9);
        expect(bounded.height).toBeCloseTo(0.5);
    });

    it('repeats a lost save with the same idempotency key and a new one for a different recipe', () => {
        const { component, keys } = createFailingComponent(() => 503);

        component['save']();
        component['retry']();

        expect(keys.length).toBe(2);
        expect(keys[1]).toBe(keys[0]);
        expect(component['saveRetryable']).toBeTrue();
        expect(component['saveError']).toContain('Riprova');

        component['recipe'].contrast = 10;
        component['save']();

        expect(keys.length).toBe(3);
        expect(keys[2]).not.toBe(keys[0]);
    });

    it('forgets the attempt after an answer that is not a lost response', () => {
        let status = 409;
        const { component, keys } = createFailingComponent(() => status);

        component['save']();

        expect(component['saveRetryable']).toBeFalse();
        expect(component['saveError']).toContain('Ricarica la traccia');

        status = 503;
        component['save']();

        expect(keys.length).toBe(2);
        expect(keys[1]).not.toBe(keys[0]);
    });

    it('builds the live crop rectangle in every drag direction', () => {
        const component = createComponent();

        const draft = component['cropBetween']({ x: 0.8, y: 0.7 }, { x: 0.2, y: 0.1 });

        expect(draft.x).toBe(0.2);
        expect(draft.y).toBe(0.1);
        expect(draft.width).toBeCloseTo(0.6);
        expect(draft.height).toBeCloseTo(0.6);
    });
});
