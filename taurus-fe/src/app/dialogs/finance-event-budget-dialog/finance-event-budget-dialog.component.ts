import { Component } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ImportsModule } from '../../imports';
import { FinancialEventSummary } from '../../module';

export interface EventBudgetResult {
    fee: number;
    costs: Array<{ description: string; amount: number }>;
}

@Component({
    selector: 'app-finance-event-budget-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './finance-event-budget-dialog.component.html',
    styleUrl: './finance-event-budget-dialog.component.scss'
})
export class FinanceEventBudgetDialogComponent {
    protected readonly event?: FinancialEventSummary;
    protected fee = 0;
    protected costs: Array<{ description: string; amount: number }> = [];

    constructor(
        private readonly dialogRef: DynamicDialogRef<FinanceEventBudgetDialogComponent>,
        private readonly config: DynamicDialogConfig<any, { event: FinancialEventSummary }>
    ) {
        this.event = this.config.inputValues?.event;
        this.fee = this.event?.expectedFee ?? 0;
        this.costs = (this.event?.expectedCostItems ?? []).map((cost) => ({ description: cost.description, amount: cost.amount }));
    }

    protected get invalidCount(): number {
        return this.costs.filter((cost) => !cost.description.trim() || cost.amount < 0).length;
    }

    protected addCost(): void {
        this.costs = [...this.costs, { description: '', amount: 0 }];
    }

    protected removeCost(index: number): void {
        this.costs = this.costs.filter((_, current) => current !== index);
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    protected save(): void {
        if (this.invalidCount > 0) return;
        const result: EventBudgetResult = {
            fee: this.fee,
            costs: this.costs.map((cost) => ({ description: cost.description.trim(), amount: cost.amount }))
        };
        this.dialogRef.close(result);
    }
}
