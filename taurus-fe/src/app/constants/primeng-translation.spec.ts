import { PRIMENG_TRANSLATION_IT } from './primeng-translation';

describe('PRIMENG_TRANSLATION_IT', () => {
    it('describes an Italian calendar starting on Monday', () => {
        expect(PRIMENG_TRANSLATION_IT.dayNamesMin).toEqual(['Do', 'Lu', 'Ma', 'Me', 'Gi', 'Ve', 'Sa']);
        expect(PRIMENG_TRANSLATION_IT.monthNames?.length).toBe(12);
        expect(PRIMENG_TRANSLATION_IT.monthNames?.[9]).toBe('Ottobre');
        expect(PRIMENG_TRANSLATION_IT.firstDayOfWeek).toBe(1);
        expect(PRIMENG_TRANSLATION_IT.today).toBe('Oggi');
        expect(PRIMENG_TRANSLATION_IT.clear).toBe('Cancella');
    });
});
