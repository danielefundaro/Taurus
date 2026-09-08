import { SimpleChange } from '@angular/core';
import { SheetsMusic } from '../../../../module';
import { MediaThumbnailComponent } from '../media-thumbnail/media-thumbnail.component';
import { ScoreWorkspaceComponent } from './score-workspace.component';

describe('ScoreWorkspaceComponent', () => {
    it('returns to a clean draft after undoing the first operation', () => {
        const component = new ScoreWorkspaceComponent(null!);
        const initial: SheetsMusic[] = [{ id: 1, order: 1, description: 'Clarinetto', media: [], instruments: [] }];
        const dirtyStates: boolean[] = [];
        component.dirtyChange.subscribe((dirty) => dirtyStates.push(dirty));
        component.scores = initial;
        component.ngOnChanges({ scores: new SimpleChange(undefined, initial, true) });

        component['addScore']();
        component['undo']();

        expect(component['draftScores']).toEqual(initial);
        expect(dirtyStates).toEqual([false, true, false]);
    });

    it('keeps selected pages selected after moving them', () => {
        const component = new ScoreWorkspaceComponent(null!);
        const initial: SheetsMusic[] = [
            { id: 1, order: 1, media: [{ index: 10 }], instruments: [] },
            { id: 2, order: 2, media: [], instruments: [] }
        ];
        component.scores = initial;
        component.ngOnChanges({ scores: new SimpleChange(undefined, initial, true) });
        component['selectedMediaIds'] = new Set([10]);
        component['moveTargetIndex'] = 1;

        component['moveSelectedPages']();

        expect(component['selectedScoreIndex']).toBe(1);
        expect(component['selectedMediaIds'].has(10)).toBeTrue();
    });

    it('selects and clears all visible parts through the shared selection bar contract', () => {
        const component = new ScoreWorkspaceComponent(null!);
        const initial: SheetsMusic[] = [
            { id: 1, order: 1, description: 'Clarinetto', media: [], instruments: [] },
            { id: 2, order: 2, description: 'Tromba', media: [], instruments: [] }
        ];
        component.scores = initial;
        component.ngOnChanges({ scores: new SimpleChange(undefined, initial, true) });

        component['toggleAllParts'](true);
        expect(component['allFilteredPartsSelected']).toBeTrue();

        component['clearPartSelection']();
        expect(component['selectedPartKeys'].size).toBe(0);
    });

    it('opens the focused page preview with Enter', () => {
        const component = new ScoreWorkspaceComponent(null!);
        const thumbnail = jasmine.createSpyObj<MediaThumbnailComponent>('MediaThumbnailComponent', ['openPreview']);
        const event = {
            key: 'Enter',
            target: null,
            currentTarget: null,
            preventDefault: jasmine.createSpy('preventDefault')
        } as unknown as KeyboardEvent;

        component['onPageKeydown'](0, 10, thumbnail, event);

        expect(event.preventDefault).toHaveBeenCalled();
        expect(thumbnail.openPreview).toHaveBeenCalled();
    });

    it('moves page navigation to the requested page within bounds', () => {
        const component = new ScoreWorkspaceComponent(null!);
        const initial: SheetsMusic[] = [{ id: 1, order: 1, media: [{ index: 10 }, { index: 11 }, { index: 12 }], instruments: [] }];
        component.scores = initial;
        component.ngOnChanges({ scores: new SimpleChange(undefined, initial, true) });

        component['navigateToPage'](8);

        expect(component['activePageIndex']).toBe(2);
    });
});
