import { Filter } from ".";
import { RoleEnums } from "../../../constants";

export class RoleFilter extends Filter<RoleEnums> {
    constructor(roleFilter?: RoleFilter) {
        super(roleFilter);
    }
}