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
});
