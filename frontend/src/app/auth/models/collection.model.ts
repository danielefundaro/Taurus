import { OrderedItem } from "./orderedItem.model";
import { Piece } from "./piece.model";

export class Collection extends OrderedItem {
    piece!: Piece;

    public clone(collection: Collection): Collection {
        const c: Collection = new Collection();

        c.id = collection.id;
        c.order = collection.order;
        c.piece = collection.piece;
        c.description = collection.description;

        return c;
    }
}