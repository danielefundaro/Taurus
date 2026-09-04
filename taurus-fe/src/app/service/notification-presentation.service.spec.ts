import { TestBed } from '@angular/core/testing';
import { NotificationPresentationService } from './notification-presentation.service';

describe('NotificationPresentationService', () => {
    let service: NotificationPresentationService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(NotificationPresentationService);
    });

    it('maps every supported source and falls back to a bell', () => {
        expect(service.icon('GENERAL')).toBe('pi pi-bell');
        expect(service.icon('CONTENT')).toBe('pi pi-file');
        expect(service.icon('CALENDAR')).toBe('pi pi-calendar');
        expect(service.icon('IDENTITY')).toBe('pi pi-users');
        expect(service.icon('TENANT')).toBe('pi pi-building');
        expect(service.icon('INVENTORY')).toBe('pi pi-box');
        expect(service.icon('FINANCE')).toBe('pi pi-wallet');
        expect(service.icon('UNKNOWN')).toBe('pi pi-bell');
    });

    it('translates notification codes into Italian presentation labels', () => {
        expect(service.sourceLabel('FINANCE')).toBe('Economia');
        expect(service.statusLabel('DELIVERED')).toBe('Consegnata');
        expect(service.operationLabel('ACCOUNT_CREATED')).toBe('Conto creato');
        expect(service.operationLabel('MOVEMENT_UNRECONCILED')).toBe('Riconciliazione annullata');
    });

    it('maps generated Italian operations and hides unknown technical codes', () => {
        expect(service.operationLabel('ALBUM_PUBBLICATO')).toBe('Album pubblicato');
        expect(service.operationLabel('NEW_OPERATION')).toBe('Operazione non catalogata');
    });
});
