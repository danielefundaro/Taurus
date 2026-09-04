import { Filter } from './filter.module';
import { RoleEnums } from '../../../constants';

export class RoleFilter extends Filter<RoleEnums> {
    constructor(roleFilter?: RoleFilter) {
        super(roleFilter);
    }
}
