import { Component, Input, OnInit } from '@angular/core';

@Component({
    selector: 'no-data',
    templateUrl: './no-data.component.html',
    styleUrls: ['./no-data.component.scss']
})
export class NoDataComponent implements OnInit {

    @Input() show?: boolean | null;
    @Input() message: string = "";

    constructor() { }

    ngOnInit(): void {
    }

}
