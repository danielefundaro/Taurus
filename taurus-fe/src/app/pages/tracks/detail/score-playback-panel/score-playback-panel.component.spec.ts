import { fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { PartPlayback, PlaybackSound, RecognitionJob, TrackPlayback } from '../../../../module';
import { RECOGNITION_POLL_MILLIS, ScorePlaybackPanelComponent } from './score-playback-panel.component';

function part(sound: Partial<PlaybackSound>, overrides: Partial<PartPlayback> = {}): PartPlayback {
    return {
        scoreId: 1,
        order: 1,
        instruments: [
            { id: 3, name: 'Flicorno tenore', configured: false },
            { id: 4, name: 'Euphonium', configured: false }
        ],
        ownPart: true,
        sound: { percussionKeys: [], configured: true, conflicting: false, ...sound },
        availability: 'AVAILABLE',
        hasPages: true,
        openReports: 0,
        ...overrides
    };
}

function job(scoreId: number, status: RecognitionJob['status'], overrides: Partial<RecognitionJob> = {}): RecognitionJob {
    return { id: scoreId * 10, scoreId, status, attempts: 1, requestedAt: '2026-10-17T10:00:00Z', engines: [], ...overrides };
}

function playback(parts: PartPlayback[], recognitionAvailable = true): TrackPlayback {
    return { trackId: 5, trackName: 'Marcia', version: 2, parts, recognitionAvailable };
}

describe('ScorePlaybackPanelComponent', () => {
    const component: any = new ScorePlaybackPanelComponent({ canManage: true, playback: () => of() } as never, null!, null!);

    it('names the instruments to configure when the part has no sound', () => {
        expect(component.soundLabel(part({ configured: false }))).toBe('Suono e trasposizione non configurati per Flicorno tenore, Euphonium');
        expect(component.soundLabel(part({ configured: false }, { instruments: [] }))).toBe('Suono e trasposizione non configurati');
    });

    it('describes the sound, the transposition and the drum sounds', () => {
        expect(component.soundLabel(part({ soundProgram: 57, transpositionSemitones: -14 }))).toBe('Trombone · -14 semitoni');
        expect(component.soundLabel(part({ soundProgram: 73, transpositionSemitones: 0 }))).toBe('Flute · in suoni reali');
        expect(component.soundLabel(part({ percussionKeys: [38, 36] }))).toBe(`Batteria: ${['Acoustic Snare', 'Bass Drum 1'].join(', ')}`);
    });
});

describe('ScorePlaybackPanelComponent recognition', () => {
    function setup(parts: PartPlayback[], canManage = true, recognitionAvailable = true) {
        const service = {
            canManage,
            playback: jasmine.createSpy().and.returnValue(of(playback(parts, recognitionAvailable))),
            recognitions: jasmine.createSpy(),
            recognize: jasmine.createSpy().and.callFake((_track: number, scoreId: number) => of([job(scoreId, 'QUEUED')])),
            recognizeAll: jasmine.createSpy().and.returnValue(of([job(1, 'QUEUED'), job(2, 'QUEUED')]))
        };
        const toast = jasmine.createSpyObj('ToastService', ['success']);
        const component: any = new ScorePlaybackPanelComponent(service as never, null!, toast);
        component.trackId = 5;
        component.reload();
        return { component, service, toast };
    }

    it('starts the recognition from «Ascolta» only for managers of parts with pages and no version', () => {
        const waiting = part({}, { availability: 'NOT_AVAILABLE', version: null });
        const { component, service } = setup([waiting]);

        expect(component.listenStartsRecognition(waiting)).toBeTrue();
        expect(component.listenHint(waiting)).toContain('Avvia il riconoscimento');
        component.listen(waiting);
        expect(service.recognize).toHaveBeenCalledWith(5, 1, jasmine.anything());
        component.ngOnDestroy();

        const listener = setup([waiting], false).component;
        expect(listener.listenStartsRecognition(waiting)).toBeFalse();
        expect(listener.listenHint(waiting)).toBe('Ascolto non ancora disponibile');
        expect(setup([waiting], true, false).component.listenStartsRecognition(waiting)).toBeFalse();
        const withoutPages = part({}, { availability: 'NOT_AVAILABLE', hasPages: false });
        expect(setup([withoutPages]).component.listenStartsRecognition(withoutPages)).toBeFalse();
    });

    it('follows the progress every few seconds and reloads the parts when a recognition ends', fakeAsync(() => {
        const { component, service } = setup([part({}, { scoreId: 1 }), part({}, { scoreId: 2, hasPages: false })]);
        component.recognizeAll();
        expect(component.recognizableParts.map((item: PartPlayback) => item.scoreId)).toEqual([]);
        expect(component.recognitionStatus(component.playback.parts[0].recognition)).toBe('Riconoscimento in coda');

        service.recognitions.and.returnValue(of([job(1, 'RUNNING', { attempts: 2 })]));
        tick(RECOGNITION_POLL_MILLIS);
        expect(service.recognitions).toHaveBeenCalledTimes(1);
        expect(component.recognitionStatus(component.playback.parts[0].recognition)).toBe('Riconoscimento in corso (tentativo 2)');
        expect(service.playback).toHaveBeenCalledTimes(1);

        service.recognitions.and.returnValue(of([job(1, 'FAILED', { errorCode: 'engineTimeout' })]));
        service.playback.and.returnValue(of(playback([part({}, { scoreId: 1, recognition: job(1, 'FAILED', { errorCode: 'engineTimeout' }) })])));
        tick(RECOGNITION_POLL_MILLIS);
        expect(service.playback).toHaveBeenCalledTimes(2);
        expect(component.recognitionStatus(component.playback.parts[0].recognition)).toBe('Riconoscimento non riuscito: i motori hanno superato il tempo massimo');

        tick(RECOGNITION_POLL_MILLIS * 3);
        expect(service.recognitions).toHaveBeenCalledTimes(2);
        component.ngOnDestroy();
    }));
});
