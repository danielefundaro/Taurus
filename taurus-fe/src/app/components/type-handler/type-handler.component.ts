import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChipModule } from 'primeng/chip';
import { InputGroup } from 'primeng/inputgroup';
import { InputGroupAddon } from 'primeng/inputgroupaddon';
import { InputTextModule } from 'primeng/inputtext';

/**
 * Controllo nudo per l'immissione dei tipi: l'etichetta è del contenitore
 * (`app-form-field`), non del controllo, come per ogni altro campo.
 */
@Component({
    selector: 'app-type-handler',
    imports: [FormsModule, InputTextModule, InputGroup, InputGroupAddon, ChipModule],
    templateUrl: './type-handler.component.html',
    styleUrl: './type-handler.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class TypeHandlerComponent {
    @Input() types?: string[];
    @Input() inputId = 'type';
    @Input() placeholder = 'Aggiungi un tipo e premi Invio';
    @Input() readOnly: boolean = false;
    @Output() typesChange = new EventEmitter<string[]>();

    protected current: string = '';

    protected inputChange($event: KeyboardEvent): void {
        if ($event.key === 'Enter') {
            this.addTypes();
        }
    }

    protected blur(): void {
        this.addTypes();
    }

    protected removeType(current: string): void {
        this.types?.splice(
            this.types.findIndex((s) => s === current),
            1
        );
        this.typesChange.emit(this.types);
    }

    private addTypes(): void {
        this.types ??= [];

        if (this.current.trim() !== '') {
            this.types.push(...this.current.split(',').map((type) => type.trim()));
            this.current = '';
            this.typesChange.emit(this.types);
        }
    }
}
