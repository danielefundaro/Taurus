import { SimpleChange } from '@angular/core';
import { SheetsMusic } from '../../../../module';
import { ConfirmService } from '../../../../service';
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

    it('selects the visible parts and clears the whole selection through the shared selection bar contract', () => {
        const component = new ScoreWorkspaceComponent(null!);
        const initial: SheetsMusic[] = [
            { id: 1, order: 1, description: 'Clarinetto', media: [], instruments: [] },
            { id: 2, order: 2, description: 'Tromba', media: [], instruments: [] }
        ];
        component.scores = initial;
        component.ngOnChanges({ scores: new SimpleChange(undefined, initial, true) });

        component['toggleAllParts'](true);
        expect(component['allFilteredPartsSelected']).toBeTrue();

        component['filterQuery'] = 'Tromba';
        component['toggleAllParts'](false);
        expect(component['selectedPartKeys'].size).toBe(0);
    });

    it('clears the selected pages and the move target when all pages are deselected', () => {
        const component = new ScoreWorkspaceComponent(null!);
        const initial: SheetsMusic[] = [
            { id: 1, order: 1, media: [{ index: 10 }, { index: 11 }], instruments: [] },
            { id: 2, order: 2, media: [], instruments: [] }
        ];
        component.scores = initial;
        component.ngOnChanges({ scores: new SimpleChange(undefined, initial, true) });

        component['toggleAllPages'](true);
        component['moveTargetIndex'] = 1;
        expect(component['allPagesSelected']).toBeTrue();

        component['toggleAllPages'](false);

        expect(component['selectedMediaIds'].size).toBe(0);
        expect(component['moveTargetIndex']).toBeNull();
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

    it('clears the review flag of the selected part and restores it with undo', () => {
        const component = new ScoreWorkspaceComponent(null!);
        const initial: SheetsMusic[] = [{ id: 1, order: 1, description: 'Ottavino', media: [], instruments: [], needsReview: true }];
        component.scores = initial;
        component.ngOnChanges({ scores: new SimpleChange(undefined, initial, true) });

        component['markReviewed']();
        expect(component['draftScores'][0].needsReview).toBeFalse();

        component['undo']();
        expect(component['draftScores'][0].needsReview).toBeTrue();
    });

    it('clears the review flag of the selected parts only', () => {
        const confirmService = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['confirmReversible']);
        confirmService.confirmReversible.and.callFake((request) => request.accept());
        const component = new ScoreWorkspaceComponent(confirmService);
        const initial: SheetsMusic[] = [
            { id: 1, order: 1, description: 'Ottavino', media: [], instruments: [], needsReview: true },
            { id: 2, order: 2, description: 'Tamburo', media: [], instruments: [], needsReview: true },
            { id: 3, order: 3, description: 'Gran cassa', media: [], instruments: [], needsReview: true }
        ];
        component.scores = initial;
        component.ngOnChanges({ scores: new SimpleChange(undefined, initial, true) });
        component['togglePart'](component['draftScores'][0]);
        component['togglePart'](component['draftScores'][1]);

        expect(component['selectedReviewCount']).toBe(2);
        component['confirmMarkSelectedReviewed']();

        expect(component['draftScores'].map((score) => score.needsReview)).toEqual([false, false, true]);
        expect(component['selectedReviewCount']).toBe(0);
    });

    it('keeps the review flag of a part shown in read only mode', () => {
        const component = new ScoreWorkspaceComponent(null!);
        const initial: SheetsMusic[] = [{ id: 1, order: 1, description: 'Ottavino', media: [], instruments: [], needsReview: true }];
        component.readOnly = true;
        component.scores = initial;
        component.ngOnChanges({ scores: new SimpleChange(undefined, initial, true) });

        component['markReviewed']();

        expect(component['draftScores'][0].needsReview).toBeTrue();
    });

    it('requests image editing only for a persisted page on a clean track', () => {
        const component = new ScoreWorkspaceComponent(null!);
        const initial: SheetsMusic[] = [{ id: 1, order: 1, description: 'Clarinetto', media: [{ index: 10 }], instruments: [] }];
        const events: number[] = [];
        component.imageEdit.subscribe((event) => events.push(event.media.index));
        component.scores = initial;
        component.ngOnChanges({ scores: new SimpleChange(undefined, initial, true) });

        component['editImage'](component['draftScores'][0], component['draftScores'][0].media![0], 0);
        component.trackDirty = true;
        component['editImage'](component['draftScores'][0], component['draftScores'][0].media![0], 0);

        expect(events).toEqual([10]);
    });
});
