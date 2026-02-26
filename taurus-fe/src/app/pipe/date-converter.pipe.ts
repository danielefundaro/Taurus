import { Pipe, PipeTransform } from "@angular/core";

@Pipe({
    name: 'date-converter',
})
export class DateConverterPipe implements PipeTransform {

    constructor() { }

    transform(date?: string | Date): Date | undefined {
        return typeof date === 'string' ? new Date(date) : date;
    }
}