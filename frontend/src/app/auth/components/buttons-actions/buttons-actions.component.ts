import { Component, Input } from '@angular/core';
import { Router } from '@angular/router';

@Component({
    selector: 'buttons-actions',
    templateUrl: './buttons-actions.component.html',
    styleUrls: ['./buttons-actions.component.scss']
})
export class ButtonsActionsComponent {
    @Input() disabled!: boolean;
    @Input() readOnly!: boolean;
    @Input() back!: string;

    constructor(private router: Router) { }

    public onBack(): void {
        this.router.navigate([this.back]);
    }
}
