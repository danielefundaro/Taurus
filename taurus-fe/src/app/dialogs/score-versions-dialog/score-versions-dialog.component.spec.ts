import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { of } from 'rxjs';
import { ScoreVersion } from '../../module';
import { ConfirmService, ScorePlaybackService, ToastService } from '../../service';
import { ScoreVersionsDialogComponent, ScoreVersionsDialogData } from './score-versions-dialog.component';

describe('ScoreVersionsDialogComponent', () => {
    let fixture: ComponentFixture<ScoreVersionsDialogComponent>;
    let dialogRef: jasmine.SpyObj<DynamicDialogRef>;
    let confirmService: jasmine.SpyObj<ConfirmService>;
    let playbackService: jasmine.SpyObj<ScorePlaybackService>;
    const version: ScoreVersion = { id: 7, scoreId: 9, versionNumber: 2, origin: 'OMR_AUDIVERIS', status: 'UNVERIFIED', measureCount: 2, checks: null as never, createdAt: '2026-10-18T10:00:00Z', current: true, pagesChanged: false };

    beforeEach(async () => {
        const data: ScoreVersionsDialogData = {
            trackId: 4,
            scoreId: 9,
            partLabel: 'Clarinetto piccolo mib',
            settings: { transpositionOverride: null, soundProgramOverride: null, soundBankOverride: null },
            instrument: { id: 3, name: 'Clarinetto piccolo', transpositionSemitones: 3, soundBank: 0, soundProgram: 71, configured: true }
        };
        dialogRef = jasmine.createSpyObj('DynamicDialogRef', ['close']);
        confirmService = jasmine.createSpyObj('ConfirmService', ['confirmDiscard']);
        // L'utente sceglie di restare nel dialogo.
        confirmService.confirmDiscard.and.callFake((request) => request.reject?.());
        playbackService = jasmine.createSpyObj('ScorePlaybackService', ['versions', 'updatePartSettings']);
        playbackService.versions.and.returnValue(of([version]));
        playbackService.updatePartSettings.and.callFake((_trackId, _scoreId, settings) => of(settings));

        await TestBed.configureTestingModule({
            imports: [ScoreVersionsDialogComponent],
            providers: [
                provideNoopAnimations(),
                { provide: DynamicDialogConfig, useValue: { data } },
                { provide: DynamicDialogRef, useValue: dialogRef },
                { provide: ScorePlaybackService, useValue: playbackService },
                { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['success']) },
                { provide: ConfirmService, useValue: confirmService }
            ]
        }).compileComponents();
        fixture = TestBed.createComponent(ScoreVersionsDialogComponent);
        fixture.detectChanges();
    });

    const component = (): { transpositionOverride: number | null; soundDirty: boolean; saveSound(): void; correct(version: ScoreVersion): void } =>
        fixture.componentInstance as never;
    const clickClose = (): void => fixture.nativeElement.querySelector('footer p-button button').click();

    it('closes at once when the sound has no unsaved changes', () => {
        clickClose();
        expect(confirmService.confirmDiscard).not.toHaveBeenCalled();
        expect(dialogRef.close).toHaveBeenCalledWith({ changed: false });
    });

    it('shows in the empty fields the sound and transposition inherited from the instrument', () => {
        const element: HTMLElement = fixture.nativeElement;
        expect(element.textContent).toContain('trasposizione di Clarinetto piccolo');
        expect(element.querySelector<HTMLInputElement>('#partTransposition')?.placeholder).toBe('+3 dallo strumento');
        expect(element.querySelector<HTMLInputElement>('#partBank')?.placeholder).toBe('0 dallo strumento');
        expect(element.querySelector('p-select')?.textContent).toContain('Come lo strumento: Clarinet');
    });

    it('asks before closing or opening the editor with an unsaved sound, and not after saving it', () => {
        component().transpositionOverride = -2;
        fixture.detectChanges();
        expect(component().soundDirty).toBeTrue();

        clickClose();
        component().correct(version);
        expect(confirmService.confirmDiscard).toHaveBeenCalledTimes(2);
        expect(dialogRef.close).not.toHaveBeenCalled();

        component().saveSound();
        fixture.detectChanges();
        expect(component().soundDirty).toBeFalse();
        clickClose();
        expect(confirmService.confirmDiscard).toHaveBeenCalledTimes(2);
        expect(dialogRef.close).toHaveBeenCalledWith({ changed: true });
    });
});
