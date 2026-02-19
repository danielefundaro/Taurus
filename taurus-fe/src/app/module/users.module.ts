import { ChildrenEntities } from "./children-entities.module";
import { CommonFieldsOpenSearch } from "./common-fields-open-search.module";

export class Users extends CommonFieldsOpenSearch {
    lastName?: string;
    birthDate?: Date;
    email?: string;
    tenants?: Array<ChildrenEntities>;
    roles?: Array<string>;
    active?: boolean;
    instruments?: Array<ChildrenEntities>;
}