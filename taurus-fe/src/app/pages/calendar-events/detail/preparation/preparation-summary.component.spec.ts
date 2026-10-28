import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { RoleEnums } from '../../../../constants';
import { EventPreparationEvaluation, EventPreparationIssue } from '../../../../module';
import { KeycloakService } from '../../../../service';
import { PreparationSummaryComponent, preparationLink } from './preparation-summary.component';

const issue = (overrides: Partial<EventPreparationIssue>): EventPreparationIssue => ({
    code: 'PROGRAM_MISSING',
    area: 'PROGRAM',
    severity: 'BLOCKER',
    message: 'Componi il programma',
    details: [],
    detailCount: 0,
    ...overrides
});

const evaluation = (overrides: Partial<EventPreparationEvaluation> = {}): EventPreparationEvaluation => ({
    evaluatedAt: '2026-10-27T10:00:00+01:00',
    phase: 'PREPARATION',
    preparationStatus: 'BLOCKED',
    closureStatus: 'NOT_REQUIRED',
    completionPercent: 40,
    passedChecks: 2,
    applicableChecks: 5,
    blockerCount: 1,
    warningCount: 1,
    infoCount: 0,
    issues: [
        issue({ code: 'AVAILABILITY_INCOMPLETE', area: 'AVAILABILITY', severity: 'WARNING', message: 'Raccogli le disponibilità', action: '#availability' }),
        issue({
            code: 'SCORE_NOT_READY',
            area: 'SCORES',
            message: 'Spartiti da completare',
            action: '#preparation-program',
            details: [{ relatedId: 7, label: '1. Marcia', reason: 'NO_READY_SCORE', message: 'Nessuno spartito pronto', action: '/tracks/7' }],
            detailCount: 3
        })
    ],
    issueCount: 2,
    excludedAreas: [],
    ...overrides
});

describe('PreparationSummaryComponent', () => {
    let fixture: ComponentFixture<PreparationSummaryComponent>;
    let element: HTMLElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PreparationSummaryComponent],
            providers: [provideRouter([]), provideNoopAnimations(), { provide: KeycloakService, useValue: { currentUserRole: RoleEnums.ADMIN } }]
        }).compileComponents();
        fixture = TestBed.createComponent(PreparationSummaryComponent);
        element = fixture.nativeElement;
    });

    function render(value: EventPreparationEvaluation, administrator = true): void {
        fixture.componentRef.setInput('evaluation', value);
        fixture.componentRef.setInput('administrator', administrator);
        fixture.detectChanges();
    }

    it('states status, phase and progress in words and through the progress bar', () => {
        render(evaluation());

        expect(element.textContent).toContain('Preparazione incompleta');
        expect(element.textContent).toContain('Preparazione');
        expect(element.textContent).toContain('2 di 5 verifiche completate');
        expect(element.textContent).toContain('1 bloccante · 1 da verificare');
        const bar = element.querySelector('[role="progressbar"]')!;
        expect(bar.getAttribute('aria-valuenow')).toBe('40');
        expect(bar.getAttribute('aria-valuetext')).toBe('40%, 2 di 5 verifiche completate');
    });

    it('lists blockers before warnings, each with its link and its rows', () => {
        render(evaluation());

        const items = Array.from(element.querySelectorAll('#preparation-issue-list > ul > li'));
        expect(items.map((item) => item.querySelector('h4')!.textContent!.trim())).toEqual([jasmine.stringContaining('Spartiti da completare'), jasmine.stringContaining('Raccogli le disponibilità')]);
        const links = Array.from(items[0].querySelectorAll('a')).map((link) => [link.textContent!.trim(), link.getAttribute('href')]);
        expect(links).toEqual([
            ['Vai al programma', '/#preparation-program'],
            ['Apri la traccia', '/tracks/7']
        ]);
        expect(items[0].textContent).toContain('e altri 2');
    });

    it('shows blockers at once and keeps only warnings behind «Mostra criticità», moving focus to the list', fakeAsync(() => {
        render(evaluation({ blockerCount: 0, preparationStatus: 'ATTENTION', issues: [issue({ severity: 'WARNING' })], issueCount: 1 }));
        document.body.appendChild(element);
        const list = element.querySelector('#preparation-issue-list') as HTMLElement;
        const toggle = element.querySelector('button[aria-controls="preparation-issue-list"]') as HTMLButtonElement;
        expect(list.hidden).toBeTrue();
        expect(toggle.getAttribute('aria-expanded')).toBe('false');
        expect(toggle.textContent).toContain('Mostra criticità (1)');

        toggle.click();
        fixture.detectChanges();
        tick();

        expect(list.hidden).toBeFalse();
        expect(toggle.getAttribute('aria-expanded')).toBe('true');
        expect(document.activeElement?.id).toBe('preparation-issues-heading');
        element.remove();
    }));

    it('tells administrators which areas their modules leave out, and nobody else', () => {
        render(evaluation({ excludedAreas: ['MATERIALS', 'BUDGET'] }));
        expect(element.textContent).toContain('Materiali, Preventivo: le aree sono richieste dal piano');

        fixture = TestBed.createComponent(PreparationSummaryComponent);
        element = fixture.nativeElement;
        render(evaluation({ excludedAreas: ['MATERIALS'] }), false);
        expect(element.textContent).not.toContain('Aree escluse');
    });

    it('says when the backend returned only the first issues', () => {
        render(evaluation({ issueCount: 25 }));

        expect(element.textContent).toContain('Sono mostrate le prime 2 di 25 criticità');
    });

    it('turns an action into router inputs', () => {
        expect(preparationLink(undefined)).toBeUndefined();
        expect(preparationLink('#presence')).toEqual({ label: 'Vai alle presenze', commands: [], fragment: 'presence' });
        expect(preparationLink('/finance?section=movements&reconciled=false&eventId=3')).toEqual({
            label: 'Apri i movimenti',
            commands: ['/finance'],
            queryParams: { section: 'movements', reconciled: 'false', eventId: '3' }
        });
        expect(preparationLink('/inventory/items/5')).toEqual({ label: 'Apri l’oggetto', commands: ['/inventory/items/5'], queryParams: undefined });
    });
});
