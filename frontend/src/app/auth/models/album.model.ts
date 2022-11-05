import { Collection } from "./collection.model";
import { CommonFields } from "./commonFields.model";

export class Album extends CommonFields {
    name!: string;
    date?: Date;
    collections?: Array<Collection>;
}