import { Pipe, PipeTransform } from "@angular/core";

@Pipe({
    name: 'enum-converter',
})
export class EnumConverterPipe<T> implements PipeTransform {

    constructor() { }

    transform(enums: T): Array<T> {
        return enums ? Object.values(enums) : [];
    }
}