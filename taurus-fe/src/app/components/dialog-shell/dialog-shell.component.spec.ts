import { DialogShellComponent } from './dialog-shell.component';
import { ConfirmService } from '../../service/confirm.service';

describe('DialogShellComponent', () => {
    let confirmService: jasmine.SpyObj<ConfirmService>;
    let component: DialogShellComponent;

    beforeEach(() => {
        confirmService = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['confirmDiscard']);
        component = new DialogShellComponent(confirmService);
    });

    it('reveals validation and emits confirm only when the form is valid', () => {
        const emit = spyOn(component.confirm, 'emit');
        component.invalidCount = 1;

        component.submit();

        expect(component.validationAttempted).toBeTrue();
        expect(emit).not.toHaveBeenCalled();

        component.invalidCount = 0;
        component.submit();
        expect(emit).toHaveBeenCalled();
    });

    it('asks before cancelling after an interaction', () => {
        const emit = spyOn(component.cancel, 'emit');
        component.markAsInteracted();

        component.requestCancel();

        expect(confirmService.confirmDiscard).toHaveBeenCalled();
        expect(emit).not.toHaveBeenCalled();

        confirmService.confirmDiscard.calls.mostRecent().args[0].accept();
        expect(emit).toHaveBeenCalled();
    });

    it('cancels immediately when nothing changed', () => {
        const emit = spyOn(component.cancel, 'emit');

        component.requestCancel();

        expect(confirmService.confirmDiscard).not.toHaveBeenCalled();
        expect(emit).toHaveBeenCalled();
    });
});
