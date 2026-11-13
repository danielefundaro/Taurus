import { Component, ChangeDetectionStrategy } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { SelectButtonModule } from 'primeng/selectbutton';
import { ToggleButtonModule } from 'primeng/togglebutton';
import { ToggleButtonDisabledDirective } from './toggle-button-disabled.directive';

@Component({
    standalone: true,
    imports: [FormsModule, SelectButtonModule, ToggleButtonModule, ToggleButtonDisabledDirective],
    changeDetection: ChangeDetectionStrategy.Eager,
    template: `
        <p-togglebutton id="solo" [(ngModel)]="solo" onLabel="S" offLabel="S" [ariaLabel]="soloLabel" [disabled]="locked" />
        <p-selectbutton id="layout" aria-label="Vista elenco" [(ngModel)]="layout" [options]="options" [disabled]="layoutLocked" />
    `
})
class HostComponent {
    solo = false;
    soloLabel = 'Solo Clarinetto';
    locked = false;
    layout = 'list';
    layoutLocked = false;
    options = ['list', 'grid'];
}

describe('ToggleButtonDisabledDirective', () => {
    let fixture: ComponentFixture<HostComponent>;
    const element = (selector: string): HTMLElement => fixture.nativeElement.querySelector(selector);
    const options = (): HTMLElement[] => Array.from(element('#layout').querySelectorAll<HTMLElement>('p-togglebutton'));
    // ngModel scrive valore e stato disabilitato in un microtask, e nel SelectButton passa per due ngModel annidati;
    // il MutationObserver richiama la direttiva in un altro microtask. Più cicli di rilevamento, poi un giro del ciclo
    // degli eventi.
    const settle = async (): Promise<void> => {
        for (let cycle = 0; cycle < 3; cycle++) {
            fixture.detectChanges();
            await fixture.whenStable();
        }
        await new Promise((resolve) => setTimeout(resolve));
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [HostComponent] }).compileComponents();
        fixture = TestBed.createComponent(HostComponent);
        await settle();
    });

    // Ruolo e nome li dà PrimeNG: se li togliesse di nuovo, come nella 19.1, la direttiva dovrebbe tornare a scriverli.
    it('trova in un ToggleButton di PrimeNG il ruolo di pulsante e il nome, e non aggiunge aria-disabled se è attivo', async () => {
        const toggle = element('#solo');

        expect(toggle.getAttribute('role')).toBe('button');
        expect(toggle.getAttribute('aria-pressed')).toBe('false');
        expect(toggle.getAttribute('aria-label')).toBe('Solo Clarinetto');
        expect(toggle.hasAttribute('aria-disabled')).toBeFalse();

        fixture.componentInstance.soloLabel = 'Solo Tromba';
        await settle();

        expect(element('#solo').getAttribute('aria-label')).toBe('Solo Tromba');
    });

    it('segnala con aria-disabled un ToggleButton disabilitato', async () => {
        fixture.componentInstance.locked = true;
        await settle();
        expect(element('#solo').getAttribute('aria-disabled')).toBe('true');

        fixture.componentInstance.locked = false;
        await settle();
        expect(element('#solo').hasAttribute('aria-disabled')).toBeFalse();
    });

    it('lascia al SelectButton il ruolo di gruppo e il suo nome, con opzioni che sono pulsanti', () => {
        const group = element('#layout');

        expect(group.getAttribute('role')).toBe('group');
        expect(group.getAttribute('aria-label')).toBe('Vista elenco');
        expect(options().length).toBe(2);
        expect(options().every((option) => option.getAttribute('role') === 'button')).toBeTrue();
        expect(options().map((option) => option.getAttribute('aria-pressed'))).toEqual(['true', 'false']);
        expect(options().some((option) => option.hasAttribute('aria-disabled'))).toBeFalse();
    });

    it('segnala con aria-disabled le opzioni di un SelectButton disabilitato, anche quelle aggiunte dopo', async () => {
        fixture.componentInstance.layoutLocked = true;
        await settle();
        expect(options().every((option) => option.getAttribute('aria-disabled') === 'true')).toBeTrue();

        fixture.componentInstance.options = ['list', 'grid', 'calendar'];
        await settle();
        expect(options().length).toBe(3);
        expect(options().every((option) => option.getAttribute('aria-disabled') === 'true')).toBeTrue();

        fixture.componentInstance.layoutLocked = false;
        await settle();
        expect(options().some((option) => option.hasAttribute('aria-disabled'))).toBeFalse();
    });
});
