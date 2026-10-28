import { HttpErrorResponse } from '@angular/common/http';
import { EventPreparationIssue } from '../../../../module';
import { actionLabel, formatDuration, isAnchor, isExplicitChoice, isVersionConflict, issueSeverity, profileConfiguration, sortIssues, statusSeverity } from './preparation-labels';

const issue = (code: string, severity: EventPreparationIssue['severity']): EventPreparationIssue => ({ code, area: 'PROGRAM', severity, message: code, details: [], detailCount: 0 });

describe('preparation labels', () => {
    it('lists blockers first, then warnings and suggestions, keeping the order of the backend within a severity', () => {
        const sorted = sortIssues([issue('a', 'INFO'), issue('b', 'WARNING'), issue('c', 'BLOCKER'), issue('d', 'WARNING'), issue('e', 'BLOCKER')]);

        expect(sorted.map((value) => value.code)).toEqual(['c', 'e', 'b', 'd', 'a']);
    });

    it('leaves the explicit choices of each profile undecided', () => {
        expect(profileConfiguration('PERFORMANCE').materialsRequired).toBeNull();
        expect(profileConfiguration('PERFORMANCE').programRequired).toBeTrue();
        expect(profileConfiguration('REHEARSAL').locationRequired).toBeNull();
        expect(profileConfiguration('REHEARSAL').materialsRequired).toBeNull();
        expect(profileConfiguration('REHEARSAL').presenceClosureRequired).toBeFalse();
        expect(profileConfiguration('OTHER').locationRequired).toBeNull();
        expect(profileConfiguration('OTHER').availabilityRequired).toBeNull();
        expect(profileConfiguration('OTHER').minimumAvailableParticipants).toBeUndefined();
        expect(isExplicitChoice('OTHER', 'availabilityRequired')).toBeTrue();
        expect(isExplicitChoice('PERFORMANCE', 'budgetRequired')).toBeFalse();
    });

    it('does not require the areas of a module that is off', () => {
        const configuration = profileConfiguration('PERFORMANCE', { inventory: false, finance: false });

        expect(configuration.materialsRequired).toBeFalse();
        expect(configuration.budgetRequired).toBeFalse();
        expect(configuration.financialClosureRequired).toBeFalse();
        expect(configuration.version).toBe(0);
    });

    it('names the place of each action and tells anchors from routes', () => {
        expect(actionLabel('#preparation-program')).toBe('Vai al programma');
        expect(actionLabel('#preparation-finance')).toBe('Vai al consuntivo');
        expect(actionLabel('/tracks/7')).toBe('Apri la traccia');
        expect(actionLabel('/inventory/items/5')).toBe('Apri l’oggetto');
        expect(actionLabel('/finance?section=movements')).toBe('Apri i movimenti');
        expect(actionLabel('/altro')).toBe('Apri');
        expect(isAnchor('#presence')).toBeTrue();
        expect(isAnchor('/tracks/7')).toBeFalse();
        expect(isAnchor(undefined)).toBeFalse();
    });

    it('gives every status and severity a tag colour', () => {
        expect(statusSeverity('READY')).toBe('success');
        expect(statusSeverity('BLOCKED')).toBe('danger');
        expect(statusSeverity('UNKNOWN')).toBe('danger');
        expect(statusSeverity('ATTENTION')).toBe('warn');
        expect(statusSeverity('NOT_CONFIGURED')).toBe('secondary');
        expect(issueSeverity('BLOCKER')).toBe('danger');
        expect(issueSeverity('WARNING')).toBe('warn');
        expect(issueSeverity('INFO')).toBe('info');
    });

    it('formats durations and recognizes a version conflict', () => {
        expect(formatDuration(185)).toBe('3:05');
        expect(formatDuration(undefined)).toBe('');
        expect(isVersionConflict(new HttpErrorResponse({ status: 409 }))).toBeTrue();
        expect(isVersionConflict(new HttpErrorResponse({ status: 400 }))).toBeFalse();
        expect(isVersionConflict(new Error('boom'))).toBeFalse();
    });
});
