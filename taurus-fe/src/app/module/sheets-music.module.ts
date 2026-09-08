import { ChildrenEntities } from './children-entities.module';

export class SheetsMusic {
    id?: number;
    description?: string;
    order?: number;
    media?: Array<ChildrenEntities>;
    instruments?: Array<ChildrenEntities>;
    needsReview?: boolean;
}
