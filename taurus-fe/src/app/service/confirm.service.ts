import { Injectable } from '@angular/core';
import { Confirmation, ConfirmationService } from 'primeng/api';

export interface ConfirmRequest {
    title: string;
    consequence: string;
    actionLabel: string;
    accept: () => void;
    reject?: () => void;
    key?: string;
}
@Injectable({ providedIn: 'root' })
export class ConfirmService {
    constructor(private readonly confirmationService: ConfirmationService) {}
    confirmDestructive(request: ConfirmRequest): void {
        this.open(request, 'pi pi-trash', { severity: 'danger' }, 'Annulla');
    }
    confirmReversible(request: ConfirmRequest): void {
        this.open(request, 'pi pi-exclamation-triangle', { severity: 'secondary', outlined: true }, 'Annulla');
    }
    confirmDiscard(request: ConfirmRequest): void {
        this.open({ ...request, key: request.key ?? 'guard' }, 'pi pi-info-circle', { severity: 'secondary', outlined: true }, 'Rimani');
    }
    private open(request: ConfirmRequest, icon: string, acceptButtonProps: Confirmation['acceptButtonProps'], rejectLabel: string): void {
        this.confirmationService.confirm({
            key: request.key,
            header: request.title,
            message: request.consequence,
            icon,
            closeOnEscape: true,
            acceptLabel: request.actionLabel,
            rejectLabel,
            acceptButtonProps,
            rejectButtonProps: { severity: 'secondary', text: true },
            accept: request.accept,
            reject: request.reject
        });
    }
}
