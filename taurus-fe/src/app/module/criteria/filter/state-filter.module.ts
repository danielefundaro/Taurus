import { Filter } from './filter.module';
import { StateEnums } from '../../../constants';

export class StateFilter extends Filter<StateEnums> {
    constructor(stateFilter?: StateFilter) {
        super(stateFilter);
    }
}
