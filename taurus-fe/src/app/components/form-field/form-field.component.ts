import { booleanAttribute, Component, Input } from '@angular/core';

@Component({ selector: 'app-form-field', standalone: true, templateUrl: './form-field.component.html', styleUrl: './form-field.component.scss' })
export class FormFieldComponent {
    @Input({ required: true }) label = '';
    @Input() forId?: string;
    @Input({ transform: booleanAttribute }) required = false;
    @Input() error?: string;
    @Input() hint?: string;
}
