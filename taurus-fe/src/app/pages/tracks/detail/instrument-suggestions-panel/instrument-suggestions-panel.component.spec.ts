import { HttpErrorResponse } from '@angular/common/http';
import { SimpleChange } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { InstrumentSuggestionName, InstrumentSuggestions, Tracks } from '../../../../module';
import { ApiErrorService, ToastService, TracksService } from '../../../../service';
import { InstrumentSuggestionsPanelComponent } from './instrument-suggestions-panel.component';

function name(overrides: Partial<InstrumentSuggestionName> = {}): InstrumentSuggestionName {
    return {
        normalizedName: 'clarinetto basso',
        proposedName: 'Clarinetto basso',
        catalogKeyword: 'clarinetto basso',
        action: 'CREATE',
        candidates: [],
        parts: [
            { sheetMusicId: 11, readText: 'Clarinetto basso 1°', source: 'PDF_TEXT', hasInstruments: false, possibleScore: false },
            { sheetMusicId: 12, readText: 'Clarinetto basso e Fagotto', source: 'OCR', hasInstruments: true, possibleScore: false },
            { sheetMusicId: 13, readText: 'Partitura', source: 'PDF_TEXT', hasInstruments: false, possibleScore: true }
        ],
        ...overrides
    };
}

function suggestions(names: InstrumentSuggestionName[], trackVersion = 4): InstrumentSuggestions {
    return { trackVersion, names };
}

function conflict(key: string): HttpErrorResponse {
    return new HttpErrorResponse({ status: 409, error: { message: `error.${key}` } });
}

describe('InstrumentSuggestionsPanelComponent', () => {
    function setup(read: InstrumentSuggestions = suggestions([name()])) {
        const tracks = jasmine.createSpyObj<TracksService>('TracksService', ['getInstrumentSuggestions', 'applyInstrumentSuggestions']);
        tracks.getInstrumentSuggestions.and.returnValue(of(read));
        const toast = jasmine.createSpyObj<ToastService>('ToastService', ['success']);
        const component: any = new InstrumentSuggestionsPanelComponent(tracks, new ApiErrorService(), toast);
        const openParts: number[][] = [];
        component.openPartsChange.subscribe((ids: number[]) => openParts.push(ids));
        component.trackId = 5;
        component.trackVersion = 4;
        component.scores = [{ id: 11, description: 'Clarinetto basso 1°' }, { id: 12, description: '', instruments: [{ index: 3, name: 'Fagotto' }] }, { id: 13 }];
        component.instruments = [
            { id: 3, name: 'Fagotto', usersCount: 2 },
            { id: 4, name: 'Clarinetto 1°', usersCount: 0 }
        ];
        component.ngOnChanges({ trackId: new SimpleChange(undefined, 5, true) });
        return { component, tracks, toast, openParts };
    }

    it('preselects only the parts without instruments that are not a probable score', () => {
        const { component, openParts } = setup();

        const draft = component.drafts[0];
        expect(draft.choice).toBe('CREATE');
        expect(draft.createName).toBe('Clarinetto basso');
        expect([...draft.selected]).toEqual([11]);
        expect(openParts).toEqual([[11, 12, 13]]);
    });

    it('proposes the only candidate and lists the candidates before the other instruments', () => {
        const { component } = setup(
            suggestions([
                name({ action: 'ASSOCIATE', candidates: [{ id: 7, name: 'Clarinetto basso', usersCount: 0 }] }),
                name({
                    normalizedName: 'corno',
                    proposedName: 'Corno',
                    action: 'ASSOCIATE',
                    candidates: [
                        { id: 8, name: 'Corno 1°', usersCount: 3 },
                        { id: 9, name: 'Corno 2°', usersCount: 1 }
                    ]
                })
            ])
        );

        expect(component.drafts[0].instrumentId).toBe(7);
        expect(component.drafts[1].instrumentId).toBeNull();
        expect(component.options(component.drafts[1]).map((option: { name: string }) => option.name)).toEqual(['Corno 1°', 'Corno 2°', 'Fagotto', 'Clarinetto 1°']);
        expect(component.canApply).toBeFalse();
    });

    it('warns that nobody plays a new instrument or one without members', () => {
        const { component } = setup(suggestions([name(), name({ normalizedName: 'fagotto', action: 'ASSOCIATE', candidates: [{ id: 3, name: 'Fagotto', usersCount: 2 }] })]));

        expect(component.withoutMembers(component.drafts[0])).toBeTrue();
        expect(component.withoutMembers(component.drafts[1])).toBeFalse();
        component.drafts[1].instrumentId = 4;
        expect(component.withoutMembers(component.drafts[1])).toBeTrue();
        component.drafts[0].choice = 'IGNORE';
        expect(component.withoutMembers(component.drafts[0])).toBeFalse();
    });

    it('names the parts with their position in the track', () => {
        const { component } = setup();

        expect(component.partTitle(component.drafts[0].name.parts[0])).toBe('1. Clarinetto basso 1°');
        expect(component.partTitle(component.drafts[0].name.parts[1])).toBe('2. Fagotto');
        expect(component.partTitle(component.drafts[0].name.parts[2])).toBe('3. Parte senza nome');
        expect(component.partTitle({ sheetMusicId: 99 })).toBe('Parte');
    });

    it('sends the decided names with the edited name and the selected parts, and leaves the others for later', () => {
        const { component, tracks, toast } = setup(suggestions([name(), name({ normalizedName: 'arpa', proposedName: 'Arpa' }), name({ normalizedName: 'chitarra', proposedName: 'Chitarra' })]));
        const updated: Tracks = { id: 5, version: 5, scores: [] };
        tracks.applyInstrumentSuggestions.and.returnValue(of(updated));
        const applied: Tracks[] = [];
        component.applied.subscribe((track: Tracks) => applied.push(track));
        component.drafts[0].createName = '  Clarinetto basso in Sib ';
        component.togglePart(component.drafts[0], component.drafts[0].name.parts[1], true);
        component.drafts[1].choice = 'IGNORE';
        component.drafts[2].choice = 'LATER';

        component.apply();

        expect(tracks.applyInstrumentSuggestions).toHaveBeenCalledWith(
            5,
            {
                trackVersion: 4,
                decisions: [
                    { normalizedName: 'clarinetto basso', action: 'CREATE', name: 'Clarinetto basso in Sib', sheetMusicIds: [11, 12] },
                    { normalizedName: 'arpa', action: 'IGNORE' }
                ]
            },
            jasmine.anything()
        );
        expect(applied).toEqual([updated]);
        expect(toast.success).toHaveBeenCalledWith('Strumenti applicati', '1 strumento creato, 1 nome ignorato. Le parti che hanno ricevuto uno strumento sono da verificare.');
    });

    it('does not apply while the workspace has unsaved changes or a choice is incomplete', () => {
        const { component, tracks } = setup();

        component.drafts[0].createName = ' ';
        expect(component.canApply).toBeFalse();
        component.drafts[0].createName = 'Clarinetto basso';
        component.disabled = true;
        expect(component.canApply).toBeFalse();
        component.apply();
        component.disabled = false;
        component.drafts[0].choice = 'LATER';
        expect(component.canApply).toBeFalse();

        expect(tracks.applyInstrumentSuggestions).not.toHaveBeenCalled();
    });

    it('reads the names again when the name to create already exists', () => {
        const { component, tracks } = setup();
        tracks.applyInstrumentSuggestions.and.returnValue(throwError(() => conflict('instrument.name.exists')));
        tracks.getInstrumentSuggestions.and.returnValue(of(suggestions([name({ action: 'ASSOCIATE', candidates: [{ id: 7, name: 'Clarinetto basso', usersCount: 0 }] })])));

        component.apply();

        expect(tracks.getInstrumentSuggestions).toHaveBeenCalledTimes(2);
        expect(component.drafts[0].choice).toBe('ASSOCIATE');
        expect(component.errorDetail).toContain('Esiste già uno strumento con questo nome');
    });

    it('asks the page to read the track again when it changed', () => {
        const { component, tracks } = setup();
        tracks.applyInstrumentSuggestions.and.returnValue(throwError(() => conflict('version.conflict')));
        const stale: void[] = [];
        component.trackStale.subscribe(() => stale.push(undefined));

        component.apply();

        expect(stale.length).toBe(1);
        expect(tracks.getInstrumentSuggestions).toHaveBeenCalledTimes(1);
        expect(component.errorDetail).toBeDefined();
    });

    it('leaves any other error to the global toast', () => {
        const { component, tracks } = setup();
        tracks.applyInstrumentSuggestions.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

        component.apply();

        expect(component.errorDetail).toBeUndefined();
        expect(component.applying).toBeFalse();
    });

    it('reads the names again with a new version of the track and clears the labels when reading fails', () => {
        const { component, tracks, openParts } = setup();
        tracks.getInstrumentSuggestions.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

        component.trackVersion = 5;
        component.ngOnChanges({ trackVersion: new SimpleChange(4, 5, false) });

        expect(component.drafts).toEqual([]);
        expect(openParts.at(-1)).toEqual([]);
    });
});

describe('InstrumentSuggestionsPanelComponent template', () => {
    async function render(read: InstrumentSuggestions, disabled = false) {
        const tracks = jasmine.createSpyObj<TracksService>('TracksService', ['getInstrumentSuggestions', 'applyInstrumentSuggestions']);
        tracks.getInstrumentSuggestions.and.returnValue(of(read));
        await TestBed.configureTestingModule({
            imports: [InstrumentSuggestionsPanelComponent],
            providers: [provideNoopAnimations(), { provide: TracksService, useValue: tracks }, { provide: ToastService, useValue: jasmine.createSpyObj('ToastService', ['success']) }]
        }).compileComponents();
        const fixture = TestBed.createComponent(InstrumentSuggestionsPanelComponent);
        fixture.componentRef.setInput('trackId', 5);
        fixture.componentRef.setInput('disabled', disabled);
        fixture.detectChanges();
        return fixture.nativeElement as HTMLElement;
    }

    it('shows nothing without open names', async () => {
        const element = await render(suggestions([]));

        expect(element.querySelector('.instrument-suggestions')).toBeNull();
    });

    it('shows the names with the probable score and the parts that already have instruments', async () => {
        const element = await render(suggestions([name()]));

        expect(element.textContent).toContain('Strumenti da aggiungere');
        expect(element.textContent).toContain('Possibile partitura');
        expect(element.textContent).toContain('Ha già strumenti');
        expect(element.textContent).toContain('Nessun socio suona ancora questo strumento');
        expect(element.textContent).toContain('Letto sulla scansione: «Clarinetto basso e Fagotto»');
    });

    it('explains why the panel waits while the workspace has unsaved changes', async () => {
        const element = await render(suggestions([name()]), true);

        expect(element.textContent).toContain('Salva la traccia prima di aggiungere gli strumenti');
        expect((element.querySelector('input[type="text"]') as HTMLInputElement).disabled).toBeTrue();
    });
});
