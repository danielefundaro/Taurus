import { Filter } from ".";
import { StateEnums } from "../../../constants";

export class StateFilter extends Filter<StateEnums> {
    constructor(stateFilter?: StateFilter) {
        super(stateFilter);
    }
}