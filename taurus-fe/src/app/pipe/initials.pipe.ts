import { Pipe, PipeTransform } from "@angular/core";

@Pipe({
    name: 'initials',
})
export class InitialsPipe implements PipeTransform {

    constructor() { }

    transform(name1: string, name2?: string): string {
        if (!name2) {
            return (name1 ?? '').split(" ").slice(0, 2).map(s => s[0]).join("").toUpperCase();
        }

        return [(name1 ?? '').slice(0, 1).toUpperCase(), (name2 ?? '').slice(0, 1).toUpperCase()].join('')
    }
}