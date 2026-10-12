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

/** Adds calendar months to a date, clamping to the last day of the target month as the backend does. */
export const addMonths = (value: Date, months: number): Date => {
    const target = new Date(value.getFullYear(), value.getMonth() + months, 1);
    const lastDay = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate();
    target.setDate(Math.min(value.getDate(), lastDay));
    return target;
};
