import { StateEnums } from "../constants";
import { ChildrenEntities } from "./children-entities.module";
import { CommonFieldsOpenSearch } from "./common-fields-open-search.module";

export class Albums extends CommonFieldsOpenSearch {
    date?: Date;
    state?: StateEnums;
    tracks?: Array<ChildrenEntities>;
}