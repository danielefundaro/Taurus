import { PartPlayback, ScoreChecks } from '../module';
import { checkIssues, checksSummary, isRecognitionActive, originLabel, partLabel, recognitionLabel } from './playback-labels';

const CLEAN: ScoreChecks = {
    measureCount: 32,
    wrongDurationCount: 0,
    wrongDurationMeasures: [],
    clefChanges: 0,
    octaveClefs: 0,
    percussionClef: false,
    keyChanges: 0,
    timeChanges: 0,
    unusualTimeSignatures: 0,
    forwardRepeats: 1,
    backwardRepeats: 1,
    openRepeats: 0,
    endings: 2,
    segni: 0,
    codas: 0,
    daCapo: 0,
    dalSegno: 0,
    fine: 0,
    tempo: true,
    emptyMeasures: 0,
    notes: 120,
    chordNotes: 0
};

function part(overrides: Partial<PartPlayback> = {}): PartPlayback {
    return {
        scoreId: 7,
        order: 3,
        instruments: [],
        ownPart: false,
        sound: { percussionKeys: [], configured: false, conflicting: false },
        availability: 'AVAILABLE',
        hasPages: true,
        openReports: 0,
        ...overrides
    };
}

describe('playback labels', () => {
    it('names a part by description, then instruments, then order', () => {
        const instruments = [
            { id: 1, name: 'Tromba', configured: true },
            { id: 2, name: 'Cornetta', configured: true }
        ];

        expect(partLabel(part({ description: ' Prima tromba ', instruments }))).toBe('Prima tromba');
        expect(partLabel(part({ description: '  ', instruments }))).toBe('Tromba, Cornetta');
        expect(partLabel(part())).toBe('Parte 3');
    });

    it('lists the problems found by the automatic checks', () => {
        expect(checkIssues(null)).toEqual([]);
        expect(checkIssues(CLEAN)).toEqual([]);
        expect(checkIssues({ ...CLEAN, wrongDurationCount: 3, wrongDurationMeasures: ['4', '9'], openRepeats: 1, octaveClefs: 1, emptyMeasures: 1, tempo: false })).toEqual([
            '3 battute con durata errata (battute 4, 9…)',
            'ritornelli aperti e non chiusi',
            "chiave all'ottava da verificare",
            '1 battuta vuota',
            'indicazione di tempo assente'
        ]);
    });

    it('summarises the version state for listeners without the missing tempo', () => {
        const version = { id: 1, versionNumber: 2, origin: 'UPLOAD_MUSICXML' as const, measureCount: 32, pagesChanged: false };

        expect(checksSummary(part())).toBeNull();
        expect(checksSummary(part({ version: { ...version, status: 'APPROVED', checks: { ...CLEAN, tempo: false } } }))).toBeNull();
        expect(checksSummary(part({ version: { ...version, status: 'UNVERIFIED', pagesChanged: true, checks: { ...CLEAN, wrongDurationCount: 1, wrongDurationMeasures: ['12'] } } }))).toBe(
            'Versione non verificata · pagine modificate dopo questa versione · 1 battuta con durata errata (battute 12)'
        );
        expect(originLabel('UPLOAD_MUSICXML')).toBe('File MusicXML');
    });

    it('describes the recognitions that are queued, running or failed', () => {
        const job = { id: 1, scoreId: 7, attempts: 1, requestedAt: '2026-10-17T10:00:00Z', engines: [] };

        expect(recognitionLabel(null)).toBeNull();
        expect(recognitionLabel({ ...job, status: 'QUEUED' })).toBe('Riconoscimento in coda');
        expect(recognitionLabel({ ...job, status: 'RUNNING' })).toBe('Riconoscimento in corso');
        expect(recognitionLabel({ ...job, status: 'FAILED', errorCode: 'tooManyPages' })).toBe('Riconoscimento non riuscito: la parte ha troppe pagine');
        expect(recognitionLabel({ ...job, status: 'FAILED', errorCode: 'mystery' })).toBe('Riconoscimento non riuscito: errore imprevisto');
        expect(recognitionLabel({ ...job, status: 'DONE' })).toBeNull();
        expect(isRecognitionActive({ ...job, status: 'RUNNING' })).toBeTrue();
        expect(isRecognitionActive({ ...job, status: 'DONE' })).toBeFalse();
        expect(originLabel('OMR_HOMR')).toBe('Riconoscimento (homr)');
    });
});
