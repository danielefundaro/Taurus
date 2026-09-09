import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { PdfAnnotations } from '../../module/pdf-annotations.module';
import { PdfManipulatorDialogComponent } from './pdf-manipulator-dialog.component';

describe('PdfManipulatorDialogComponent', () => {
    function createComponent(annotations?: PdfAnnotations): { component: PdfManipulatorDialogComponent; close: jasmine.Spy; detectChanges: jasmine.Spy } {
        const close = jasmine.createSpy('close');
        const detectChanges = jasmine.createSpy('detectChanges');
        const component = new PdfManipulatorDialogComponent(
            { close } as unknown as DynamicDialogRef,
            { data: { file: new File([], 'score.pdf'), annotations } } as DynamicDialogConfig,
            null!,
            { confirmDiscard: jasmine.createSpy('confirmDiscard') } as any,
            { detectChanges } as any
        );
        return { component, close, detectChanges };
    }

    it('materializes the conditional canvas before the first preview render', () => {
        const { component, detectChanges } = createComponent();
        const renderPreview = spyOn<any>(component, 'renderPreview');

        component['activateCropEditor']();

        expect(component['cropMode']).toBeTrue();
        expect(detectChanges).toHaveBeenCalledBefore(renderPreview);
        expect(renderPreview).toHaveBeenCalledTimes(1);
    });

    it('positions slider values consistently with the image editor tooltips', () => {
        const { component } = createComponent();

        expect(component['sliderPosition'](-100, -100, 100)).toBe(0);
        expect(component['sliderPosition'](0, -100, 100)).toBe(50);
        expect(component['sliderPosition'](100, -100, 100)).toBe(100);
        expect(component['sliderPosition'](300, 0, 255)).toBe(100);
        expect(component['sliderPosition'](180, 0, 360)).toBe(50);
        expect(component['sliderPosition'](null, 0, 255)).toBe(0);
    });

    it('normalizes legacy negative deskew values to the zero-to-360 range', () => {
        const { component } = createComponent({
            excludedPages: [],
            cropRegions: [],
            pageTransforms: [
                {
                    page: 1,
                    recipeVersion: 1,
                    rotationQuarterTurns: 0,
                    deskewDegrees: -0.8,
                    crops: [],
                    grayscale: false,
                    brightness: 0,
                    contrast: 0,
                    autoContrast: false,
                    threshold: null
                }
            ]
        });

        expect(component['pageRecipes'].get(1)?.deskewDegrees).toBeCloseTo(359.2);
        expect(component['isZeroAngle'](360)).toBeTrue();
    });

    it('prevents confirmation when every loaded page is excluded', () => {
        const { component, close } = createComponent();
        component['pages'] = [1, 2, 3];
        component['excludedPages'] = new Set([1, 2, 3]);

        expect(component['allExcluded']).toBeTrue();

        component['confirm']();
        expect(close).not.toHaveBeenCalled();
    });

    it('applies all manual adjustments to every page while preserving exclusions', () => {
        const { component } = createComponent();
        component['pages'] = [1, 2, 3];
        component['cropPageNum'] = 1;
        component['excludedPages'] = new Set([3]);
        component['recipe'] = {
            expectedTrackVersion: 0,
            recipeVersion: 1,
            rotationQuarterTurns: 1,
            deskewDegrees: 1.5,
            crops: [],
            grayscale: true,
            brightness: 12,
            contrast: -8,
            autoContrast: true,
            threshold: null
        };

        component['applyCropToAll']();

        expect(component['pageRecipes'].size).toBe(3);
        expect(component['pageRecipes'].get(2)).toEqual(component['pageRecipes'].get(1));
        expect(component['pageRecipes'].get(2)).not.toBe(component['pageRecipes'].get(1));
        expect(component['excludedPages']).toEqual(new Set([3]));
    });

    it('creates the two ordered crop zones from split presets', () => {
        const { component } = createComponent();
        component['cropPageNum'] = 4;

        component['splitCrop']('vertical');

        expect(component['pageCrops']).toEqual([
            { page: 4, x: 0, y: 0, width: 0.5, height: 1 },
            { page: 4, x: 0.5, y: 0, width: 0.5, height: 1 }
        ]);
        expect(component['pageRecipes'].get(4)?.crops).toEqual([
            { x: 0, y: 0, width: 0.5, height: 1 },
            { x: 0.5, y: 0, width: 0.5, height: 1 }
        ]);
    });

    it('updates a crop in place immediately without changing its order or duplicating it', () => {
        const { component } = createComponent();
        component['cropPageNum'] = 1;
        component['pageCrops'] = [
            { page: 1, x: 0, y: 0, width: 0.4, height: 1 },
            { page: 1, x: 0.6, y: 0, width: 0.4, height: 1 }
        ];

        component['editPageCrop'](0);
        component['updateCrop']('width', 0.5);

        expect(component['pageCrops']).toEqual([
            { page: 1, x: 0, y: 0, width: 0.5, height: 1 },
            { page: 1, x: 0.6, y: 0, width: 0.4, height: 1 }
        ]);
        expect(component['pageRecipes'].get(1)?.crops).toHaveSize(2);
    });

    it('keeps drawing outside the canvas through pointer capture and commits on release', () => {
        const { component } = createComponent();
        const canvas = document.createElement('canvas');
        spyOn(canvas, 'getBoundingClientRect').and.returnValue({ left: 0, top: 0, width: 100, height: 100 } as DOMRect);
        component['previewCanvasRef'] = { nativeElement: canvas } as any;
        component['cropPageNum'] = 1;
        component['cropDrawing'] = true;
        const pointerTarget = {
            setPointerCapture: jasmine.createSpy('setPointerCapture'),
            hasPointerCapture: jasmine.createSpy('hasPointerCapture').and.returnValue(true),
            releasePointerCapture: jasmine.createSpy('releasePointerCapture')
        };
        const pointer = (clientX: number, clientY: number) => ({ currentTarget: pointerTarget, pointerId: 7, clientX, clientY, preventDefault: jasmine.createSpy('preventDefault') }) as unknown as PointerEvent;

        component['onCropPointerDown'](pointer(10, 20));
        component['onCropPointerMove'](pointer(120, 130));
        component['onCropPointerUp'](pointer(120, 130));

        expect(pointerTarget.setPointerCapture).toHaveBeenCalledOnceWith(7);
        expect(pointerTarget.releasePointerCapture).toHaveBeenCalledOnceWith(7);
        expect(component['pageCrops']).toEqual([{ page: 1, x: 0.1, y: 0.2, width: 0.9, height: 0.8 }]);
        expect(component['editingCropIndex']).toBe(0);
        expect(component['cropDrawing']).toBeFalse();
        expect(component['pageRecipes'].get(1)?.crops).toEqual([{ x: 0.1, y: 0.2, width: 0.9, height: 0.8 }]);
    });

    it('does not add more than eight crop zones', () => {
        const { component } = createComponent();
        component['cropPageNum'] = 1;
        component['pageCrops'] = Array.from({ length: 8 }, (_, index) => ({ page: 1, x: index / 10, y: 0, width: 0.05, height: 1 }));
        component['cropRect'] = { x: 0, y: 0, width: 0.5, height: 0.5 };

        component['applyCrop']();

        expect(component['pageCrops']).toHaveSize(8);
    });

    it('restores existing page transforms and legacy crop annotations', () => {
        const { component } = createComponent({
            excludedPages: [2],
            cropRegions: [{ page: 1, x: 0, y: 0, width: 0.5, height: 1 }],
            pageTransforms: [
                {
                    page: 2,
                    recipeVersion: 1,
                    rotationQuarterTurns: 0,
                    deskewDegrees: 0,
                    crops: [],
                    grayscale: false,
                    brightness: 20,
                    contrast: 0,
                    autoContrast: false,
                    threshold: null
                }
            ]
        });

        expect(component['excludedPages']).toEqual(new Set([2]));
        expect(component['pageRecipes'].get(1)?.crops).toHaveSize(1);
        expect(component['pageRecipes'].get(2)?.brightness).toBe(20);
    });
});
