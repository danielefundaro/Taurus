import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ConfirmationService } from 'primeng/api';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { of } from 'rxjs';
import { ScoreEditorData } from '../../module';
import { ConfirmService, MediaService, ScoreAudioService, ScorePlaybackService, ScoreRendererService } from '../../service';
import { ScoreEditorDialogComponent, ScoreEditorDialogData } from './score-editor-dialog.component';

const XML = `<?xml version="1.0" encoding="UTF-8"?><score-partwise version="4.0"><part-list><score-part id="P1"><part-name>Tuba</part-name></score-part></part-list><part id="P1">
<measure number="1"><attributes><divisions>1</divisions><time><beats>2</beats><beat-type>4</beat-type></time><clef><sign>F</sign><line>4</line></clef></attributes>
<note><pitch><step>C</step><octave>3</octave></pitch><duration>1</duration><voice>1</voice><type>quarter</type></note>
<note><pitch><step>D</step><octave>3</octave></pitch><duration>1</duration><voice>1</voice><type>quarter</type></note></measure>
<measure number="2"><note><pitch><step>E</step><octave>3</octave></pitch><duration>2</duration><voice>1</voice><type>half</type></note></measure>
</part></score-partwise>`;

describe('ScoreEditorDialogComponent', () => {
    let fixture: ComponentFixture<ScoreEditorDialogComponent>;
    let renderer: jasmine.SpyObj<ScoreRendererService>;
    let playbackService: jasmine.SpyObj<ScorePlaybackService>;
    let dialogRef: jasmine.SpyObj<DynamicDialogRef>;

    beforeEach(async () => {
        const editor: ScoreEditorData = {
            version: { id: 7, scoreId: 9, versionNumber: 2, origin: 'OMR_AUDIVERIS', status: 'UNVERIFIED', measureCount: 2, checks: null as never, createdAt: '2026-10-18T10:00:00Z', current: true, pagesChanged: false },
            musicXml: XML,
            pages: [],
            layout: null
        };
        const data: ScoreEditorDialogData = { trackId: 4, scoreId: 9, versionId: 7, partLabel: 'Tuba', sound: { percussionKeys: [], configured: true, conflicting: false } };
        renderer = jasmine.createSpyObj('ScoreRendererService', ['render']);
        renderer.render.and.callFake(async (xml: string) => [`<svg><g class="measure" id="${/<measure[^>]* id="([^"]+)"/.exec(xml)![1]}"></g></svg>`]);
        playbackService = jasmine.createSpyObj('ScorePlaybackService', ['editor', 'errorReports', 'correct']);
        playbackService.editor.and.returnValue(of(editor));
        playbackService.errorReports.and.returnValue(of([]));
        playbackService.correct.and.returnValue(of({ ...editor.version, id: 8, versionNumber: 3, origin: 'CORRECTION' }));
        dialogRef = jasmine.createSpyObj('DynamicDialogRef', ['close']);

        await TestBed.configureTestingModule({
            imports: [ScoreEditorDialogComponent],
            providers: [
                provideNoopAnimations(),
                ConfirmationService,
                { provide: DynamicDialogConfig, useValue: { data } },
                { provide: DynamicDialogRef, useValue: dialogRef },
                { provide: ScorePlaybackService, useValue: playbackService },
                { provide: ScoreRendererService, useValue: renderer },
                { provide: MediaService, useValue: jasmine.createSpyObj('MediaService', ['streamImage']) },
                { provide: ScoreAudioService, useValue: jasmine.createSpyObj('ScoreAudioService', ['ready', 'load', 'play', 'pause', 'setChannel']) },
                { provide: ConfirmService, useValue: jasmine.createSpyObj('ConfirmService', ['confirmDiscard']) }
            ]
        }).compileComponents();
        fixture = TestBed.createComponent(ScoreEditorDialogComponent);
    });

    it('opens the version and draws the notation inside the editor', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const component = fixture.componentInstance as unknown as { status: string; selectionLabel: string };
        expect(component.status).toBe('ready');
        expect(renderer.render).toHaveBeenCalledTimes(1);
        expect(fixture.nativeElement.querySelector('.score-editor__notation svg')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('.score-editor__notation g.measure.is-current-measure')).not.toBeNull();
        expect(component.selectionLabel).toBe('Battuta 1 · Do3 · semiminima');
    });

    it('draws quick edits once, at the next frame', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        const component = fixture.componentInstance as unknown as { move(steps: number): void };

        component.move(1);
        component.move(1);
        component.move(1);
        expect(renderer.render).toHaveBeenCalledTimes(1);
        await new Promise((resolve) => requestAnimationFrame(resolve));
        await fixture.whenStable();

        expect(renderer.render).toHaveBeenCalledTimes(2);
        expect(renderer.render.calls.mostRecent().args[0].split('<step>F</step>').length - 1).toBe(1);
    });

    it('goes back to the track from the top left button, asking first when there are unsaved corrections', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        const confirmService = TestBed.inject(ConfirmService) as jasmine.SpyObj<ConfirmService>;
        const back = (): void => fixture.nativeElement.querySelector('.score-editor__header > p-button:first-child button').click();

        back();
        expect(dialogRef.close).toHaveBeenCalledTimes(1);
        expect(confirmService.confirmDiscard).not.toHaveBeenCalled();

        (fixture.componentInstance as unknown as { move(steps: number): void }).move(1);
        back();
        expect(confirmService.confirmDiscard).toHaveBeenCalled();
        expect(dialogRef.close).toHaveBeenCalledTimes(1);
    });

    it('saves a correction with the origin of the measures', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        const component = fixture.componentInstance as unknown as { move(steps: number): void; save(approve: boolean): void; dirty: boolean };

        component.move(1);
        expect(component.dirty).toBeTrue();
        component.save(true);

        const correction = playbackService.correct.calls.mostRecent().args[2];
        expect(correction).toEqual(jasmine.objectContaining({ parentVersionId: 7, approve: true, measureSources: [0, 1] }));
        expect(correction.content.split('<step>D</step>').length - 1).toBe(2);
        expect(correction.content).not.toContain('taurus-');
        expect(dialogRef.close).toHaveBeenCalledWith(jasmine.objectContaining({ versionNumber: 3 }));
    });
});
