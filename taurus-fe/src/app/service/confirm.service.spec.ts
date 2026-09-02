import { ConfirmationService } from 'primeng/api';
import { ConfirmService } from './confirm.service';

describe('ConfirmService', () => {
    let primeConfirm: jasmine.SpyObj<ConfirmationService>;
    let service: ConfirmService;

    beforeEach(() => {
        primeConfirm = jasmine.createSpyObj<ConfirmationService>('ConfirmationService', ['confirm']);
        service = new ConfirmService(primeConfirm);
    });

    it('configures destructive confirmations consistently', () => {
        const accept = jasmine.createSpy('accept');

        service.confirmDestructive({
            title: 'Elimina elemento',
            consequence: 'L’elemento verrà eliminato definitivamente.',
            actionLabel: 'Elimina',
            accept
        });

        const confirmation = primeConfirm.confirm.calls.mostRecent().args[0];
        expect(confirmation.header).toBe('Elimina elemento');
        expect(confirmation.acceptButtonProps?.severity).toBe('danger');
        confirmation.accept?.();
        expect(accept).toHaveBeenCalled();
    });

    it('uses the guard key for discard confirmations', () => {
        service.confirmDiscard({
            title: 'Modifiche non salvate',
            consequence: 'Le modifiche andranno perse.',
            actionLabel: 'Esci',
            accept: () => undefined
        });

        const confirmation = primeConfirm.confirm.calls.mostRecent().args[0];
        expect(confirmation.key).toBe('guard');
        expect(confirmation.rejectLabel).toBe('Rimani');
    });
});
