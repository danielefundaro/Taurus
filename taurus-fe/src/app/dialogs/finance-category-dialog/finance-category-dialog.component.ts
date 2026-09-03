import { Component } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ImportsModule } from '../../imports';
import { FinancialCategory } from '../../module';

@Component({
    selector: 'app-finance-category-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './finance-category-dialog.component.html'
})
export class FinanceCategoryDialogComponent {
    protected readonly category: FinancialCategory;

    protected readonly directions = [
        { label: 'Entrata', value: 'INCOME' },
        { label: 'Uscita', value: 'EXPENSE' },
        { label: 'Entrata e uscita', value: 'BOTH' }
    ];

    constructor(
        private readonly dialogRef: DynamicDialogRef<FinanceCategoryDialogComponent>,
        private readonly config: DynamicDialogConfig<any, { category?: FinancialCategory }>
    ) {
        const existing = this.config.inputValues?.category;
        this.category = existing ? { ...existing } : { name: '', direction: 'EXPENSE', displayOrder: 0, active: true };
    }

    protected get editing(): boolean {
        return !!this.category.id;
    }

    protected get nameError(): string | undefined {
        return this.category.name?.trim() ? undefined : 'Indica come si chiama la categoria.';
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    protected save(): void {
        if (this.nameError) return;
        this.category.name = this.category.name.trim();
        this.dialogRef.close(this.category);
    }
}
