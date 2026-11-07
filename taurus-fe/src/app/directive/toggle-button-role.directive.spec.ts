import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { SelectButtonModule } from 'primeng/selectbutton';
import { ToggleButtonModule } from 'primeng/togglebutton';
import { ToggleButtonRoleDirective } from './toggle-button-role.directive';

@Component({
    standalone: true,
    imports: [FormsModule, SelectButtonModule, ToggleButtonModule, ToggleButtonRoleDirective],
    template: `
        <p-togglebutton id="solo" [(ngModel)]="solo" onLabel="S" offLabel="S" [ariaLabel]="soloLabel" [disabled]="locked" />
        <p-selectbutton id="layout" aria-label="Vista elenco" [(ngModel)]="layout" [options]="options" />
    `
})
class HostComponent {
    solo = false;
    soloLabel = 'Solo Clarinetto';
    locked = false;
    layout = 'list';
    options = ['list', 'grid'];
}

describe('ToggleButtonRoleDirective', () => {
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
        await TestBed.configureTestingModule({ imports: [HostComponent], providers: [provideNoopAnimations()] }).compileComponents();
        fixture = TestBed.createComponent(HostComponent);
        await settle();
    });

    it('dà a un ToggleButton il ruolo di pulsante, accanto ad aria-pressed', () => {
        const toggle = element('#solo');

        expect(toggle.getAttribute('role')).toBe('button');
        expect(toggle.getAttribute('aria-pressed')).toBe('false');
        expect(toggle.hasAttribute('aria-disabled')).toBeFalse();
    });

    it("porta l'ariaLabel del ToggleButton sull'elemento e lo segue quando cambia", async () => {
        expect(element('#solo').getAttribute('aria-label')).toBe('Solo Clarinetto');

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

    it('lascia al SelectButton il ruolo di gruppo e il suo nome, e rende pulsanti le opzioni', () => {
        const group = element('#layout');

        expect(group.getAttribute('role')).toBe('group');
        expect(group.getAttribute('aria-label')).toBe('Vista elenco');
        expect(options().length).toBe(2);
        expect(options().every((option) => option.getAttribute('role') === 'button')).toBeTrue();
        expect(options().map((option) => option.getAttribute('aria-pressed'))).toEqual(['true', 'false']);
    });

    it('rende pulsanti anche le opzioni aggiunte dopo il primo disegno', async () => {
        fixture.componentInstance.options = ['list', 'grid', 'calendar'];
        await settle();

        expect(options().length).toBe(3);
        expect(options().every((option) => option.getAttribute('role') === 'button')).toBeTrue();
    });
});
