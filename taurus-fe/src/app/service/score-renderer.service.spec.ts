import { withoutPartNames } from './score-renderer.service';

describe('withoutPartNames', () => {
    it('drops the names written beside the staves and keeps the notes and their ids', () => {
        const xml = `<?xml version="1.0" encoding="UTF-8"?><score-partwise version="4.0"><part-list><score-part id="P1"><part-name>Flicorno tenore in Sib</part-name><part-name-display><display-text>Flic. ten.</display-text></part-name-display><part-abbreviation>Flic.</part-abbreviation></score-part></part-list><part id="P1"><measure number="1" id="te1-m1"><note id="te1-n1"><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration><type>whole</type></note></measure></part></score-partwise>`;

        const result = new DOMParser().parseFromString(withoutPartNames(xml), 'application/xml');

        expect(result.querySelector('part-name')?.textContent).toBe('');
        expect(result.querySelector('part-abbreviation')?.textContent).toBe('');
        expect(result.querySelector('part-name-display')).toBeNull();
        expect(result.querySelector('note')?.getAttribute('id')).toBe('te1-n1');
        expect(result.querySelector('measure')?.getAttribute('id')).toBe('te1-m1');
    });

    it('leaves an unreadable document to Verovio', () => {
        expect(withoutPartNames('<score-partwise>')).toBe('<score-partwise>');
    });
});
