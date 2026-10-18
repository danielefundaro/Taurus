import { measureElements } from './notation';
import { measure } from './score-player.testing';

function drawn(numbers: string[]): Element[] {
    return numbers.map((number) => {
        const element = document.createElementNS('http://www.w3.org/2000/svg', 'g');
        element.setAttribute('data-n', number);
        return element;
    });
}

function numbered(numbers: string[]) {
    return numbers.map((number, index) => measure(index, { number }));
}

describe('measureElements', () => {
    it('keeps the multi-measure rest highlighted for the measures it groups', () => {
        const elements = drawn(['1', '6', '7']);
        const result = measureElements(numbered(['1', '2', '3', '4', '5', '6', '7']), elements);

        expect(result).toEqual([elements[0], elements[0], elements[0], elements[0], elements[0], elements[1], elements[2]]);
    });

    it('follows the order when measure numbers repeat', () => {
        const elements = drawn(['35', '36', '36', '37']);
        const result = measureElements(numbered(['35', '36', '36', '37']), elements);

        expect(result).toEqual(elements);
    });

    it('matches by position when the numbers differ but the counts are equal', () => {
        const elements = drawn(['1', '2', '3']);
        const result = measureElements(numbered(['0', '1', '2']), elements);

        expect(result).toEqual(elements);
    });

    it('leaves the measures before the first drawn measure without element', () => {
        const elements = drawn(['3']);

        expect(measureElements(numbered(['1', '2', '3', '4']), elements)).toEqual([undefined, undefined, elements[0], elements[0]]);
        expect(measureElements(numbered(['1']), [])).toEqual([undefined]);
    });
});
