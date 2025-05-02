import { NgFor, NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChipModule } from 'primeng/chip';
import { FloatLabelModule } from 'primeng/floatlabel';
import { InputGroup } from 'primeng/inputgroup';
import { InputGroupAddon } from 'primeng/inputgroupaddon';
import { InputTextModule } from 'primeng/inputtext';

@Component({
    selector: 'app-type-handler',
    imports: [
        NgIf,
        NgFor,
        FormsModule,
        FloatLabelModule,
        InputTextModule,
        InputGroup,
        InputGroupAddon,
        ChipModule,
    ],
    templateUrl: './type-handler.component.html',
    styleUrl: './type-handler.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TypeHandlerComponent {
    @Input("types") types?: string[];
    @Input("variant") variant: 'over' | 'on' | 'in';
    @Input("label") label: string;
    @Output("typesChange") typesChange = new EventEmitter<string[]>();

    protected current: string;

    constructor() {
        this.variant = 'over';
        this.current = "";
        this.label = "Type";
    }

    protected inputChange($event: KeyboardEvent): void {
        if ($event.key === "Enter") {
            this.addTypes();
        }
    }

    protected blur(): void {
        this.addTypes();
    }

    protected removeType(current: string): void {
        this.types?.splice(this.types.findIndex(s => s === current), 1);
        this.typesChange.emit(this.types);
    }

    private addTypes(): void {
        this.types ??= [];

        if (this.current.trim() !== "") {
            this.types.push(...this.current.split(",").map(type => type.trim()));
            this.current = "";
            this.typesChange.emit(this.types);
        }
    }
}
