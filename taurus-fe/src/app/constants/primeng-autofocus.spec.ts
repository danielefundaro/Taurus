import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { AutoFocus } from 'primeng/autofocus';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { InputOtpModule } from 'primeng/inputotp';
import { SelectModule } from 'primeng/select';
import { neutralizePrimeNgAutoFocusDefault } from './primeng-autofocus';

@Component({
    standalone: true,
    imports: [FormsModule, AutoFocus, ButtonModule, DatePickerModule, InputOtpModule, SelectModule],
    template: `
        <div id="plain">
            <p-button label="Salva" />
            <p-select [options]="options" ariaLabel="Evento" />
            <p-datepicker ariaLabel="Data" />
        </div>
        <div id="otp"><p-inputotp [(ngModel)]="code" [length]="6" [autofocus]="true" /></div>
        <input id="directive" [pAutoFocus]="focus" aria-label="Campo" />
    `
})
class HostComponent {
    options = ['Concerto', 'Prova'];
    code = '';
    focus: boolean | undefined = undefined;
}

describe('neutralizePrimeNgAutoFocusDefault', () => {
    let fixture: ComponentFixture<HostComponent>;
    const element = (selector: string): HTMLElement => fixture.nativeElement.querySelector(selector);

    beforeAll(() => neutralizePrimeNgAutoFocusDefault());

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
        fixture = TestBed.createComponent(HostComponent);
        fixture.detectChanges();
        await fixture.whenStable();
    });

    it('non dà l’attributo autofocus a pulsanti, select e datepicker che non lo chiedono', () => {
        expect(element('#plain button')).not.toBeNull();
        expect(element('#plain').querySelector('[autofocus]')).toBeNull();
    });

    it('lascia l’autofocus ai componenti che lo chiedono', () => {
        expect(element('#otp').querySelector('[autofocus]')).not.toBeNull();
    });

    it('segue la direttiva quando il valore passa da assente a vero e torna falso', () => {
        expect(element('#directive').hasAttribute('autofocus')).toBeFalse();

        fixture.componentInstance.focus = true;
        fixture.detectChanges();
        expect(element('#directive').hasAttribute('autofocus')).toBeTrue();

        fixture.componentInstance.focus = false;
        fixture.detectChanges();
        expect(element('#directive').hasAttribute('autofocus')).toBeFalse();
    });

    it('si applica una volta sola anche se chiamata di nuovo', () => {
        const patched = AutoFocus.prototype.onAfterContentChecked;

        neutralizePrimeNgAutoFocusDefault();

        expect(AutoFocus.prototype.onAfterContentChecked).toBe(patched);
    });
});
