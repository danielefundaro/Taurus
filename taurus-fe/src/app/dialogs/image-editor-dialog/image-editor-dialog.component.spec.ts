import { DynamicDialogConfig } from 'primeng/dynamicdialog';
import { ImageEditorDialogComponent } from './image-editor-dialog.component';

describe('ImageEditorDialogComponent', () => {
    function createComponent(): ImageEditorDialogComponent {
        return new ImageEditorDialogComponent({ data: { trackVersion: 1 } } as DynamicDialogConfig, null!, null!, null!, null!);
    }

    it('positions slider values across their configured range', () => {
        const component = createComponent();

        expect(component['sliderPosition'](-100, -100, 100)).toBe(0);
        expect(component['sliderPosition'](0, -100, 100)).toBe(50);
        expect(component['sliderPosition'](100, -100, 100)).toBe(100);
        expect(component['sliderPosition'](300, 0, 255)).toBe(100);
        expect(component['sliderPosition'](null, 0, 255)).toBe(0);
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
});
