import { CommonFields } from '.';

export class Notices extends CommonFields {
    name!: string;
    message?: string;
    readDate?: Date;
    source?: string;
    severity?: 'INFO' | 'SUCCESS' | 'WARNING';
    targetPath?: string;
    sourceEventKey?: string;
    preferencePolicy?: 'CONFIGURABLE' | 'REQUIRED';
    snoozedUntil?: Date;
    snoozeRevision?: number;
    entityVersion?: number;
}
