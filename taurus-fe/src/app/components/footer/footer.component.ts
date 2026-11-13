import { Component, ChangeDetectionStrategy } from '@angular/core';

@Component({
    standalone: true,
    selector: 'app-footer',
    templateUrl: 'footer.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: 'footer.component.scss'
})
export class FooterComponent {}
