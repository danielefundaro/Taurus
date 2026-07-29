export enum StateEnums {
    DRAFT = 'DRAFT',
    COMPLETE = 'COMPLETE',
    PUBLIC = 'PUBLIC',
    TRASHED = 'TRASHED',
}

export interface StateLabel {
    name: string;
    code: StateEnums;
}

export const StateLabelsMap: StateLabel[] = [
    { name: 'Bozza', code: StateEnums.DRAFT },
    { name: 'Completo', code: StateEnums.COMPLETE },
    { name: 'Pubblico', code: StateEnums.PUBLIC },
    // { name: 'Cestinato', code: StateEnums.TRASHED },
];