import { Component, Input } from '@angular/core';
import { ImportsModule } from '../../imports';

export const INVENTORY_EXPIRATION_WARNING_DAYS = 30;

type ExpirationSeverity = 'danger' | 'warn' | 'secondary';

interface ExpirationBadge {
    label: string;
    severity: ExpirationSeverity;
    icon: string;
}

@Component({
    selector: 'app-inventory-expiration-badge',
    standalone: true,
    imports: [ImportsModule],
    template: `
        @if (badge; as value) {
            <p-tag [value]="value.label" [severity]="value.severity" [icon]="value.icon" />
        }
    `
})
export class InventoryExpirationBadgeComponent {
    @Input() expirationDate?: string | Date | null;
    @Input() compact = false;

    protected get badge(): ExpirationBadge | undefined {
        const expirationDate = this.normalizeDate(this.expirationDate);
        if (!expirationDate || !/^\d{4}-\d{2}-\d{2}$/.test(expirationDate)) return undefined;

        const today = new Date();
        const todayValue = this.toLocalDate(today);
        const warningLimit = new Date(today.getFullYear(), today.getMonth(), today.getDate());
        warningLimit.setDate(warningLimit.getDate() + INVENTORY_EXPIRATION_WARNING_DAYS);
        const formattedDate = this.formatDate(expirationDate);

        if (expirationDate < todayValue) {
            return { label: this.compact ? 'Scaduto' : `Scaduto il ${formattedDate}`, severity: 'danger', icon: 'pi pi-times-circle' };
        }
        if (expirationDate <= this.toLocalDate(warningLimit)) {
            return { label: this.compact ? 'In scadenza' : `Scade il ${formattedDate}`, severity: 'warn', icon: 'pi pi-exclamation-triangle' };
        }
        return { label: this.compact ? 'Programmato' : `Scadenza ${formattedDate}`, severity: 'secondary', icon: 'pi pi-calendar' };
    }

    private normalizeDate(value?: string | Date | null): string | undefined {
        if (!value) return undefined;
        return value instanceof Date ? this.toLocalDate(value) : value.slice(0, 10);
    }

    private toLocalDate(value: Date): string {
        const year = value.getFullYear();
        const month = String(value.getMonth() + 1).padStart(2, '0');
        const day = String(value.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    }

    private formatDate(value: string): string {
        const [year, month, day] = value.split('-');
        return `${day}/${month}/${year}`;
    }
}
