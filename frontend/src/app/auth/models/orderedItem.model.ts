import { CommonFields } from "./commonFields.model";

export enum PositionEnum {
    First,
    Last
}

export class OrderedItem extends CommonFields {
    public order!: number;

    public move(list: Array<OrderedItem>, position: PositionEnum): void {
        switch (position) {
            case PositionEnum.First:
                for (let i = this.order || 1; i > 1; i--) {
                    this.changeOrder(list, -1);
                }
                break;
            case PositionEnum.Last:
                for (let i = this.order || 1; i < list.length; i++) {
                    this.changeOrder(list, 1);
                }
                break;
        }
    }

    public changeOrder(collections: Array<OrderedItem>, order: number): void {
        if (!this.order) {
            this.order = 1;
        } else {
            const elem = collections.find(elem => this.order && elem.order == this.order + order);

            if (elem) {
                elem.order = this.order;
                this.order += order;
            } else {
                if (this.order > 1 && this.order < collections.length) {
                    this.order += order;
                }
            }
        }
    }
}
