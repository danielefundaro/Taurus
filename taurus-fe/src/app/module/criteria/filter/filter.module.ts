export class Filter<T> {
    equals?: T;
    notEquals?: T;
    specified?: boolean;
    in?: T;
    notIn?: T;

    constructor(t?: Filter<T>) {
        this.equals = t?.equals;
        this.notEquals = t?.notEquals;
        this.specified = t?.specified;
        this.in = t?.in;
        this.notIn = t?.notIn;
    }
}