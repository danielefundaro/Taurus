import { CommonFieldsOpenSearch } from "./common-fields-open-search.module";
import { SheetsMusic } from "./sheets-music.module";

export class Tracks extends CommonFieldsOpenSearch {
    composer?: string;
    arranger?: string;
    type?: Array<string>;
    scores?: Array<SheetsMusic>;
}