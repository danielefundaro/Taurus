export class QueryPagination {
    q: string;
    index: number;
    size: number;
    sort: Array<string>;

    constructor(q?: string, index?: number, size?: number, sort?: Array<string>) {
        this.q = q == undefined ? "" : q;
        this.index = index == undefined ? 0 : index;
        this.size = size == undefined ? 10 : size;
        this.sort = sort == undefined ? [] : sort;
    }
}