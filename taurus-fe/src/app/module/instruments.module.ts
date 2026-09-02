import { CommonFieldsOpenSearch } from "./common-fields-open-search.module";

export class Instruments extends CommonFieldsOpenSearch {
    /** Quanti utenti hanno assegnato questo strumento. Valorizzato solo negli elenchi. */
    usersCount?: number;
}