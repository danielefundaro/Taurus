/** Formats a date picked in the browser as an ISO local date (yyyy-MM-dd), without time zone shifts. */
export const toLocalDate = (value?: Date | null): string | undefined => {
    if (!value) return undefined;
    const year = value.getFullYear();
    const month = String(value.getMonth() + 1).padStart(2, '0');
    const day = String(value.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
};

/** Parses an ISO local date (yyyy-MM-dd) as a local midnight date. */
export const fromLocalDate = (value?: string | null): Date | undefined => {
    if (!value) return undefined;
    const [year, month, day] = value.split('-').map(Number);
    return new Date(year, month - 1, day);
};

/**
 * Reads a date-only value of the API, sent as `yyyy-MM-dd` or as an instant, as a local midnight date: the calendar day
 * does not move with the time zone of the browser.
 */
export const fromApiDate = (value?: string | number | Date | null): Date | undefined => {
    if (value === undefined || value === null || value === '') return undefined;
    if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) return fromLocalDate(value);
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? undefined : new Date(date.getFullYear(), date.getMonth(), date.getDate());
};

/** Adds calendar months to a date, clamping to the last day of the target month as the backend does. */
export const addMonths = (value: Date, months: number): Date => {
    const target = new Date(value.getFullYear(), value.getMonth() + months, 1);
    const lastDay = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate();
    target.setDate(Math.min(value.getDate(), lastDay));
    return target;
};

/** Reads the wall clock of a zone as a browser-local date, to do arithmetic on the fields of that zone. */
const wallClockIn = (zone: string, moment: Date): Date => {
    const parts = new Intl.DateTimeFormat('en-GB', {
        timeZone: zone,
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false
    }).formatToParts(moment);
    const field = (type: string): number => Number(parts.find((part) => part.type === type)?.value ?? 0);
    return new Date(field('year'), field('month') - 1, field('day'), field('hour') % 24, field('minute'), field('second'));
};

/**
 * The instant of a wall clock time in a zone: the hour chosen is the one the member reads on their own clock,
 * wherever the browser happens to be. Two passes are enough to settle the offset, daylight saving included.
 */
export const instantOfWallClock = (zone: string, wall: Date): Date => {
    let guess = new Date(wall.getTime());
    for (let pass = 0; pass < 2; pass++) {
        guess = new Date(guess.getTime() - (wallClockIn(zone, guess).getTime() - wall.getTime()));
    }
    return guess;
};

/**
 * The next time the clock of the zone shows that hour, counted from the day after: it is what «Tomorrow morning»
 * means for someone whose profile lives in another time zone than the browser.
 */
export const nextMorningIn = (zone: string, hour = 8, reference: Date = new Date()): Date => {
    const wall = wallClockIn(zone, reference);
    wall.setDate(wall.getDate() + 1);
    wall.setHours(hour, 0, 0, 0);
    return instantOfWallClock(zone, wall);
};
