import { StateEnums } from '../constants';
import { CommonFieldsOpenSearch } from './common-fields-open-search.module';
import { SheetsMusic } from './sheets-music.module';

export class Tracks extends CommonFieldsOpenSearch {
    version?: number;
    subName?: string;
    composer?: string;
    arranger?: string;
    tempo?: string;
    tone?: string;
    state?: StateEnums;
    type?: Array<string>;
    scores?: Array<SheetsMusic>;
    /** Sola lettura: si modifica dalle impostazioni di ascolto. */
    tempoBpm?: number | null;
    /** Sola lettura: parte di riferimento della partitura. */
    referenceScoreId?: number | null;
}
