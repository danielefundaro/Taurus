import { HttpErrorResponse } from '@angular/common/http';
import { signal, WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ConfirmationService } from 'primeng/api';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { firstValueFrom, of, throwError } from 'rxjs';
import { ScoreEditorData } from '../../module';
import { ConfirmService, EditLockState, MediaService, ScoreAudioService, ScoreEditLockService, ScorePlaybackService, ScoreRendererService } from '../../service';
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
    let lockState: WritableSignal<EditLockState>;
    let lock: { session: string; state: () => EditLockState; readonly held: boolean; acquire: jasmine.Spy; refused: jasmine.Spy; release: jasmine.Spy };

    beforeEach(async () => {
        lockState = signal<EditLockState>({ status: 'acquiring', message: null });
        lock = {
            session: 'editor-window-1',
            state: lockState.asReadonly(),
            get held() {
                return lockState().status === 'held';
            },
            acquire: jasmine.createSpy('acquire').and.callFake(async () => {
                lockState.set({ status: 'held', message: null });
                return true;
            }),
            refused: jasmine.createSpy('refused').and.returnValue(false),
            release: jasmine.createSpy('release')
        };
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
                ConfirmationService,
                { provide: DynamicDialogConfig, useValue: { data } },
                { provide: DynamicDialogRef, useValue: dialogRef },
                { provide: ScorePlaybackService, useValue: playbackService },
                { provide: ScoreRendererService, useValue: renderer },
                { provide: MediaService, useValue: jasmine.createSpyObj('MediaService', ['streamImage']) },
                { provide: ScoreAudioService, useValue: jasmine.createSpyObj('ScoreAudioService', ['ready', 'load', 'play', 'pause', 'setChannel']) },
                { provide: ConfirmService, useValue: jasmine.createSpyObj('ConfirmService', ['confirmDiscard', 'confirmReversible']) },
                { provide: ScoreEditLockService, useValue: { forPart: () => lock } }
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

    it('selects a measure without notes and fills it with a rest, also from the checks', async () => {
        const empty = XML.replace(/<measure number="2">[\s\S]*?<\/measure>/, '<measure number="2"></measure>');
        const editor = await firstValueFrom(playbackService.editor(4, 9, 7));
        playbackService.editor.and.returnValue(of({ ...editor, musicXml: empty }));
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        const component = fixture.componentInstance as unknown as {
            emptyMeasures: { index: number }[];
            selectionLabel: string;
            selectMeasure(index: number): void;
            fillMeasure(): void;
            insert(where: 'before' | 'after'): void;
            message: string | null;
            note: { rest: boolean } | null;
        };

        expect(component.emptyMeasures.map((item) => item.index)).toEqual([1]);
        component.selectMeasure(1);
        expect(component.selectionLabel).toBe('Battuta 2 · vuota');
        component.insert('after');
        expect(component.message).toContain('La battuta è vuota');

        component.fillMeasure();
        expect(component.note?.rest).toBeTrue();
        expect(component.emptyMeasures).toEqual([]);
    });

    it('turns a measure into a multi-measure rest, asking first only when it contains notes', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        const confirmService = TestBed.inject(ConfirmService) as jasmine.SpyObj<ConfirmService>;
        const component = fixture.componentInstance as unknown as {
            measureCount: number;
            selectMeasure(index: number): void;
            addMeasures(): void;
            applyMultiRest(): void;
            measure: { index: number; multiRest: number } | null;
        };

        component.measureCount = 3;
        component.selectMeasure(0);
        component.applyMultiRest();
        expect(confirmService.confirmReversible).toHaveBeenCalledTimes(1);
        expect(component.measure?.multiRest).toBe(1);

        component.measureCount = 1;
        component.addMeasures();
        expect(component.measure?.index).toBe(1);
        component.measureCount = 4;
        component.applyMultiRest();
        expect(confirmService.confirmReversible).toHaveBeenCalledTimes(1);
        expect(component.measure?.multiRest).toBe(4);

        // Selezionando una pausa di più battute il numero mostra la sua durata, pronto da correggere.
        component.measureCount = 2;
        component.selectMeasure(0);
        expect(component.measureCount).toBe(2);
        component.selectMeasure(1);
        expect(component.measureCount).toBe(4);
    });

    it('saves a correction with the origin of the measures', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        const component = fixture.componentInstance as unknown as { move(steps: number): void; save(approve: boolean): void; dirty: boolean };

        component.move(1);
        expect(component.dirty).toBeTrue();
        component.save(true);

        const correction = playbackService.correct.calls.mostRecent().args[2];
        expect(correction).toEqual(jasmine.objectContaining({ parentVersionId: 7, approve: true, measureSources: [0, 1], origin: 'CORRECTION', editSession: 'editor-window-1' }));
        expect(correction.content.split('<step>D</step>').length - 1).toBe(2);
        expect(correction.content).not.toContain('taurus-');
        expect(dialogRef.close).toHaveBeenCalledWith(jasmine.objectContaining({ versionNumber: 3 }));

        fixture.destroy();
        expect(lock.release).toHaveBeenCalled();
    });

    it('opens in read only while someone else corrects or writes the part, until the user takes over', async () => {
        lock.acquire.and.callFake(async (takeOver = false) => {
            lockState.set(takeOver ? { status: 'held', message: null } : { status: 'locked', message: 'In modifica da Mario Rossi dalle 10:32' });
            return takeOver;
        });
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        const confirmService = TestBed.inject(ConfirmService) as jasmine.SpyObj<ConfirmService>;
        const component = fixture.componentInstance as unknown as { move(steps: number): void; save(approve: boolean): void; resolveLock(): void; dirty: boolean; message: string | null };

        const notice: HTMLElement = fixture.nativeElement.querySelector('.score-editor__lock');
        expect(notice.textContent).toContain('In modifica da Mario Rossi dalle 10:32');
        component.move(1);
        expect(component.dirty).toBeFalse();
        expect(component.message).toContain('sola lettura');

        component.resolveLock();
        expect(confirmService.confirmReversible).toHaveBeenCalled();
        confirmService.confirmReversible.calls.mostRecent().args[0].accept();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(lock.acquire).toHaveBeenCalledWith(true);
        expect(fixture.nativeElement.querySelector('.score-editor__lock')).toBeNull();
        component.move(1);
        expect(component.dirty).toBeTrue();
    });

    it('passes to read only when a save finds that someone else took over', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        const refusal = new HttpErrorResponse({ status: 409, error: { message: 'error.scoreWriting.locked' } });
        playbackService.correct.and.returnValue(throwError(() => refusal));
        lock.refused.and.callFake(() => {
            lockState.set({ status: 'lost', message: 'In modifica da Lucia Bianchi dalle 10:40' });
            return true;
        });
        const component = fixture.componentInstance as unknown as { move(steps: number): void; save(approve: boolean): void; readOnly: boolean; dirty: boolean };

        component.move(1);
        component.save(false);

        expect(lock.refused).toHaveBeenCalledWith(refusal);
        expect(component.readOnly).toBeTrue();
        expect(component.dirty).toBeTrue();
        expect(dialogRef.close).not.toHaveBeenCalled();
    });
});
