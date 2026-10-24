import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { finalize, first, firstValueFrom } from 'rxjs';
import { ImportsModule } from '../../imports';
import { ErrorReport, MeasureBox, PlaybackSound, ScoreCorrection, ScoreEditorData, ScoreVersion } from '../../module';
import { EditHistory } from '../../score-editor/edit-history';
import { excerpt } from '../../score-editor/excerpt';
import { Alteration, ALTERATIONS, CLEF_OPTIONS, KEY_OPTIONS, NOTE_TYPES, NoteType, Step, STEP_NAMES, STEPS } from '../../score-editor/notation-rules';
import { EditError, ScoreDocument, ScoreDocumentChecks } from '../../score-editor/score-document';
import * as edits from '../../score-editor/score-edits';
import { parseMusicXml } from '../../score-player/musicxml-score';
import { buildPlayback } from '../../score-player/playback-builder';
import { originLabel } from '../../score-player/playback-labels';
import { partSound, secondsAt } from '../../score-player/timeline';
import { ConfirmService, MediaService, ScoreAudioService, ScorePlaybackService, ScoreRendererService, withErrorSummary } from '../../service';

export interface ScoreEditorDialogData {
    trackId: number;
    scoreId: number;
    versionId: number;
    partLabel: string;
    /** Suono della parte, per ascoltare le battute durante la correzione. */
    sound: PlaybackSound;
}

type EditorStatus = 'loading' | 'ready' | 'error';

interface PageHighlight {
    left: number;
    top: number;
    width: number;
    height: number;
    approximate: boolean;
}

/** Dimensione dello spartito nell'editor, più grande che nel lettore per selezionare comodamente le note. */
const EDITOR_SCALE = 50;

/**
 * Editor di correzione di una versione: spartito disegnato da Verovio a sinistra, pagina originale a destra con la
 * battuta selezionata evidenziata. Le modifiche agiscono sul MusicXML e producono, al salvataggio, una nuova versione.
 */
@Component({
    selector: 'app-score-editor-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './score-editor-dialog.component.html',
    styleUrl: './score-editor-dialog.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ScoreEditorDialogComponent implements OnInit, OnDestroy {
    /** Contenitore dello spartito: esiste solo quando l'editor è pronto. */
    @ViewChild('notation') private notation?: ElementRef<HTMLElement>;
    @ViewChild('pageViewer') private pageViewer?: ElementRef<HTMLElement>;

    protected readonly data: ScoreEditorDialogData;
    protected readonly noteTypes = [...NOTE_TYPES];
    protected readonly alterations = ALTERATIONS;
    protected readonly clefs = [...CLEF_OPTIONS];
    protected readonly keys = [...KEY_OPTIONS];
    protected readonly steps = STEPS.map((step) => ({ step, label: STEP_NAMES[step] }));
    protected readonly dynamics = edits.DYNAMICS;
    protected readonly articulations = edits.ARTICULATIONS;
    protected readonly marks = edits.STRUCTURE_MARKS;
    protected readonly tuplets = edits.TUPLETS;
    protected readonly beatTypes = [1, 2, 4, 8, 16, 32].map((value) => ({ label: String(value), value }));
    protected readonly endingOptions = [
        { label: 'Nessuna', value: null },
        { label: '1ª volta', value: '1' },
        { label: '2ª volta', value: '2' },
        { label: '3ª volta', value: '3' }
    ];
    protected readonly zoomOptions = [
        { label: '100%', value: 100 },
        { label: '150%', value: 150 },
        { label: '200%', value: 200 }
    ];
    protected readonly originLabel = originLabel;

    protected status: EditorStatus = 'loading';
    protected errorMessage = '';
    protected editor?: ScoreEditorData;
    protected rendering = false;
    protected saving = false;
    protected dirty = false;
    protected message: string | null = null;
    protected activeTool = 'note';

    protected selectedId: string | null = null;
    /** Battuta selezionata quando non contiene note da selezionare. */
    private emptyMeasureId: string | null = null;
    /** Ultima battuta dell'intervallo da ascoltare, scelta con Maiusc+clic. */
    protected rangeEnd: number | null = null;
    protected note: edits.NoteInfo | null = null;
    protected measure: edits.MeasureInfo | null = null;
    protected checks: ScoreDocumentChecks = { wrongDuration: [], empty: [] };
    protected reports: ErrorReport[] = [];

    protected timeBeats = 4;
    protected timeBeatType = 4;
    protected tempo: number | null = null;
    /** Battute da aggiungere, o durata della pausa di più battute. */
    protected measureCount = 1;

    protected pageIndex = 0;
    protected zoom = 100;
    protected pageUrl: string | null = null;
    protected pageLoading = false;
    protected highlight: PageHighlight | null = null;

    protected playing = false;
    protected preparingAudio = false;

    private score?: ScoreDocument;
    private initialSnapshot = '';
    private readonly history = new EditHistory();
    private readonly pageUrls = new Map<number, string>();
    private renderToken = 0;
    private renderFrame?: number;
    private stopTimer?: number;

    constructor(
        config: DynamicDialogConfig<ScoreEditorDialogData>,
        private readonly dialogRef: DynamicDialogRef<ScoreEditorDialogComponent>,
        private readonly playbackService: ScorePlaybackService,
        private readonly mediaService: MediaService,
        private readonly renderer: ScoreRendererService,
        private readonly audio: ScoreAudioService,
        private readonly confirmService: ConfirmService,
        private readonly changeDetector: ChangeDetectorRef
    ) {
        this.data = config.data!;
    }

    async ngOnInit(): Promise<void> {
        try {
            const [editor, reports] = await Promise.all([
                firstValueFrom(this.playbackService.editor(this.data.trackId, this.data.scoreId, this.data.versionId)),
                firstValueFrom(this.playbackService.errorReports(this.data.trackId)).catch(() => [] as ErrorReport[])
            ]);
            this.editor = editor;
            this.reports = reports.filter((report) => report.scoreId === this.data.scoreId && report.status === 'OPEN');
            this.score = edits.openScore(editor.musicXml);
            this.initialSnapshot = this.score.snapshot();
            this.checks = this.score.checks();
            this.selectedId = this.score.notes()[0]?.getAttribute('id') ?? null;
            this.status = 'ready';
            // Il contenitore dello spartito compare con lo stato «pronto»: la vista va aggiornata prima di disegnare.
            this.changeDetector.detectChanges();
            this.refreshSelection();
            await this.render();
        } catch (error) {
            if (!(error instanceof EditError)) console.error(error);
            this.status = 'error';
            this.errorMessage = error instanceof EditError ? error.message : "Impossibile aprire la versione nell'editor: riprovare più tardi";
        }
        this.changeDetector.markForCheck();
    }

    ngOnDestroy(): void {
        this.stop();
        if (this.renderFrame !== undefined) cancelAnimationFrame(this.renderFrame);
        for (const url of this.pageUrls.values()) URL.revokeObjectURL(url);
    }

    protected get canUndo(): boolean {
        return this.history.canUndo;
    }

    protected get canRedo(): boolean {
        return this.history.canRedo;
    }

    protected get pages(): number[] {
        return this.editor?.pages ?? [];
    }

    protected get hasLayout(): boolean {
        return !!this.editor?.layout?.length;
    }

    /** Battute da ascoltare: quella selezionata o l'intervallo scelto. */
    protected get listenLabel(): string {
        if (!this.measure) return 'Ascolta';
        if (this.rangeEnd === null || this.rangeEnd === this.measure.index) return `Ascolta battuta ${this.measure.number}`;
        const numbers = this.measureNumbers([this.measure.index, this.rangeEnd].sort((left, right) => left - right));
        return `Ascolta battute ${numbers[0]}–${numbers[1]}`;
    }

    protected get selectionLabel(): string {
        if (this.measure?.empty) return `Battuta ${this.measure.number} · vuota`;
        if (!this.note || !this.measure) return 'Seleziona una nota sullo spartito';
        return `Battuta ${this.measure.number} · ${edits.noteLabel(this.note)}`;
    }

    protected get emptyMeasures(): { index: number; number: string }[] {
        const measures = this.score?.measures() ?? [];
        return this.checks.empty.map((index) => ({ index, number: measures[index]?.getAttribute('number') ?? String(index + 1) }));
    }

    protected get wrongMeasures(): { index: number; number: string }[] {
        const measures = this.score?.measures() ?? [];
        return this.checks.wrongDuration.map((index) => ({ index, number: measures[index]?.getAttribute('number') ?? String(index + 1) }));
    }

    @HostListener('document:keydown', ['$event'])
    protected onKey(event: KeyboardEvent): void {
        if (this.status !== 'ready' || this.saving) return;
        const target = event.target as HTMLElement | null;
        const key = event.key;
        if (target && (['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName) || target.isContentEditable || target.closest('.p-select, .p-select-overlay, .p-inputnumber, .p-confirmdialog'))) return;
        // Pulsanti e schede restano utilizzabili da tastiera con Invio, spazio e frecce.
        if (target?.closest('[role="tab"]') && key.startsWith('Arrow')) return;
        if (target?.closest('button, [role="tab"]') && (key === ' ' || key === 'Enter')) return;
        const command = event.ctrlKey || event.metaKey;
        let handled = true;
        if (command && key.toLowerCase() === 'z' && !event.shiftKey) this.undo();
        else if (command && (key.toLowerCase() === 'y' || (key.toLowerCase() === 'z' && event.shiftKey))) this.redo();
        else if (key === 'ArrowUp' || key === 'ArrowDown') this.move((key === 'ArrowUp' ? 1 : -1) * (command ? 7 : 1));
        else if (command || event.altKey) handled = false;
        else if (key === 'ArrowLeft' || key === 'ArrowRight') this.selectAdjacent(key === 'ArrowLeft' ? -1 : 1);
        else if (/^[a-g]$/i.test(key)) this.letter(key.toUpperCase() as Step);
        else if (NOTE_TYPES.some((option) => option.key === key)) this.setType(NOTE_TYPES.find((option) => option.key === key)!.type);
        else if (key === '.') this.toggleDots();
        else if (key === '0') this.toggleRest();
        else if (key === '+') this.alteration('sharp');
        else if (key === '-') this.alteration('flat');
        else if (key === '=') this.alteration('natural');
        else if (key === 'Delete' || key === 'Backspace') this.remove();
        else if (key === 'Insert' || key === 'i') this.insert('after');
        else if (key === 't') this.tie();
        else if (key === ' ') void this.togglePlay();
        else if (key === 'Escape' && this.rangeEnd !== null) {
            this.rangeEnd = null;
            this.decorate(false);
        } else handled = false;
        if (handled) {
            event.preventDefault();
            this.changeDetector.markForCheck();
        }
    }

    // Selezione

    protected onNotationClick(event: MouseEvent): void {
        if (!this.score) return;
        let target = event.target as Element | null;
        let noteId: string | null = null;
        let measureIndex: number | null = null;
        while (target && target !== this.notationHost) {
            const id = target.getAttribute('id');
            const found = id && target.tagName.toLowerCase() === 'g' ? this.score.find(id) : null;
            if (found?.tagName === 'note' && !noteId) noteId = id;
            if (found?.tagName === 'measure') {
                measureIndex = this.score.measureIndex(found);
                break;
            }
            target = target.parentElement;
        }
        if (measureIndex === null) {
            // Gli spazi vuoti tra le note non appartengono ad alcun elemento disegnato: vale la battuta che contiene il punto.
            target = this.measureAt(event.clientX, event.clientY);
            const found = target ? this.score.find(target.getAttribute('id') ?? '') : null;
            measureIndex = found?.tagName === 'measure' ? this.score.measureIndex(found) : null;
        }
        if (measureIndex === null || !target) return;
        if (event.shiftKey && this.measure) {
            this.rangeEnd = measureIndex;
            this.decorate(false);
            this.changeDetector.markForCheck();
            return;
        }
        this.rangeEnd = null;
        // Le teste delle note sono piccole: un clic accanto a una nota sceglie la nota più vicina della battuta.
        const id = noteId ?? this.nearestNote(target, event.clientX, event.clientY) ?? edits.firstNoteOf(this.score, measureIndex);
        if (id) this.select(id);
        else this.selectEmptyMeasure(measureIndex);
    }

    protected selectMeasure(index: number): void {
        if (!this.score) return;
        this.rangeEnd = null;
        const id = edits.firstNoteOf(this.score, index);
        if (id) this.select(id);
        else this.selectEmptyMeasure(index);
    }

    /** Seleziona la battuta indicata in una segnalazione. */
    protected showReport(report: ErrorReport): void {
        if (!this.score || !report.measureNumber) return;
        const index = this.score.measures().findIndex((measure) => measure.getAttribute('number') === report.measureNumber);
        if (index >= 0) this.selectMeasure(index);
    }

    // Nota

    protected setType(type: NoteType): void {
        this.noteChange((score, id) => edits.setNoteType(score, id, type));
    }

    protected toggleDots(): void {
        this.noteChange((score, id) => edits.cycleDots(score, id));
    }

    protected toggleRest(): void {
        this.noteChange((score, id) => edits.toggleRest(score, id));
    }

    protected alteration(value: Alteration): void {
        this.noteChange((score, id) => edits.setAlteration(score, id, value));
    }

    protected move(steps: number): void {
        this.noteChange((score, id) => edits.moveStep(score, id, steps));
    }

    protected letter(step: Step): void {
        this.noteChange((score, id) => edits.setPitchLetter(score, id, step));
    }

    protected insert(where: 'before' | 'after'): void {
        this.noteChange((score, id) => edits.insertNote(score, id, where));
    }

    protected addToChord(): void {
        this.noteChange((score, id) => edits.addChordNote(score, id));
    }

    protected remove(): void {
        this.noteChange((score, id) => edits.deleteNote(score, id));
    }

    protected tie(): void {
        this.noteChange((score, id) => edits.toggleTie(score, id));
    }

    protected tuplet(actual: number): void {
        this.noteChange((score, id) => edits.toggleTuplet(score, id, actual));
    }

    protected dynamic(value: edits.Dynamic | null): void {
        this.noteChange((score, id) => edits.setDynamic(score, id, value));
    }

    protected articulation(value: edits.Articulation): void {
        this.noteChange((score, id) => edits.toggleArticulation(score, id, value));
    }

    // Battuta e struttura

    protected applyTime(): void {
        this.measureChange((score, index) => edits.setTime(score, index, this.timeBeats, this.timeBeatType));
    }

    protected applyKey(fifths: number): void {
        this.measureChange((score, index) => edits.setKey(score, index, fifths));
    }

    protected applyClef(value: string): void {
        this.measureChange((score, index) => edits.setClef(score, index, value));
    }

    protected insertMeasure(where: 'before' | 'after'): void {
        this.measureChange((score, index) => edits.firstNoteOf(score, edits.insertMeasure(score, index, where)));
    }

    protected deleteMeasure(): void {
        this.measureChange((score, index) => {
            edits.deleteMeasure(score, index);
            return edits.firstNoteOf(score, Math.min(index, score.measures().length - 1));
        });
    }

    /** Aggiunge il numero di battute indicato dopo quella selezionata, ognuna con una pausa di battuta. */
    protected addMeasures(): void {
        this.measureChange((score, index) => edits.firstNoteOf(score, edits.insertMeasures(score, index, this.measureCount)));
    }

    /** Una battuta vuota riceve una pausa di battuta: da lì si inseriscono le note come nelle altre battute. */
    protected fillMeasure(): void {
        this.measureChange((score, index) => edits.fillEmptyMeasure(score, index));
    }

    /** La battuta selezionata diventa una pausa del numero di battute indicato; se contiene note, prima si chiede. */
    protected applyMultiRest(): void {
        const measure = this.measure;
        if (!measure) {
            this.measureChange(() => undefined);
            return;
        }
        const count = this.measureCount;
        const change = (): void =>
            this.measureChange((score, index) => {
                edits.setMultiRest(score, index, count);
                return edits.firstNoteOf(score, index);
            });
        if (!measure.hasNotes) {
            change();
            return;
        }
        this.confirmService.confirmReversible({
            title: 'Pausa di più battute',
            consequence: `La battuta ${measure.number} diventa una pausa ${count === 1 ? 'di battuta' : `di ${count} battute`}: le sue note andranno perse. Potrai annullare l’operazione prima del salvataggio.`,
            actionLabel: 'Trasforma in pausa',
            key: 'score-editor-discard',
            accept: change
        });
    }

    protected repeat(direction: 'forward' | 'backward'): void {
        this.measureChange((score, index) => edits.toggleRepeat(score, index, direction));
    }

    protected applyEnding(value: string | null): void {
        this.measureChange((score, index) => edits.setEnding(score, index, value));
    }

    protected mark(value: edits.StructureMark): void {
        this.measureChange((score, index) => edits.toggleMark(score, index, value));
    }

    protected applyTempo(value: number | null): void {
        this.measureChange((score, index) => edits.setTempo(score, index, value));
    }

    // Cronologia

    protected undo(): void {
        if (!this.score) return;
        const previous = this.history.undo(this.score.snapshot());
        if (previous !== null) this.restore(previous);
    }

    protected redo(): void {
        if (!this.score) return;
        const next = this.history.redo(this.score.snapshot());
        if (next !== null) this.restore(next);
    }

    // Ascolto

    protected async togglePlay(): Promise<void> {
        if (this.playing) {
            this.stop();
            return;
        }
        if (!this.score || !this.measure || this.preparingAudio) return;
        this.preparingAudio = true;
        this.changeDetector.markForCheck();
        try {
            const part = parseMusicXml(this.score.cleanXml());
            const passage = excerpt(part, this.measure.index, this.rangeEnd ?? this.measure.index);
            const built = buildPlayback([{ key: 'part', name: this.data.partLabel, part: passage, sound: partSound(this.data.sound, part.clefSign) }], passage, {
                withRepeats: false,
                tempoBpm: null,
                countIn: false
            });
            await this.audio.ready();
            await this.audio.load(built.midi);
            this.audio.setChannel(built.metronome, 0, true);
            await this.audio.play();
            this.playing = true;
            this.stopTimer = window.setTimeout(() => this.stop(), secondsAt(built.totalQuarters, built.tempo) * 1000 + 300);
        } catch {
            this.message = 'Ascolto non disponibile: riprovare più tardi';
        } finally {
            this.preparingAudio = false;
            this.changeDetector.markForCheck();
        }
    }

    // Pagine

    protected async showPage(index: number): Promise<void> {
        const pages = this.pages;
        if (!pages.length) return;
        this.pageIndex = Math.max(0, Math.min(pages.length - 1, index));
        const mediaId = pages[this.pageIndex];
        let url = this.pageUrls.get(mediaId);
        if (!url) {
            this.pageLoading = true;
            this.changeDetector.markForCheck();
            try {
                url = URL.createObjectURL(await firstValueFrom(this.mediaService.streamImage(mediaId)));
                this.pageUrls.set(mediaId, url);
            } catch {
                url = undefined;
            } finally {
                this.pageLoading = false;
            }
        }
        this.pageUrl = url ?? null;
        this.changeDetector.markForCheck();
        this.scrollToHighlight();
    }

    // Salvataggio

    protected save(approve: boolean): void {
        if (!this.score || !this.editor || this.saving || !this.dirty) return;
        this.stop();
        this.saving = true;
        const correction: ScoreCorrection = {
            parentVersionId: this.editor.version.id,
            content: this.score.cleanXml(),
            approve,
            measureSources: this.score.measureSources()
        };
        this.playbackService
            .correct(this.data.trackId, this.data.scoreId, correction, withErrorSummary('Correzione non salvata'))
            .pipe(
                first(),
                finalize(() => {
                    this.saving = false;
                    this.changeDetector.markForCheck();
                })
            )
            .subscribe((version: ScoreVersion) => {
                this.dirty = false;
                this.dialogRef.close(version);
            });
    }

    protected close(): void {
        if (!this.dirty) {
            this.dialogRef.close();
            return;
        }
        this.confirmService.confirmDiscard({
            title: 'Uscire senza salvare?',
            consequence: 'Le correzioni non salvate andranno perse.',
            actionLabel: 'Esci senza salvare',
            key: 'score-editor-discard',
            accept: () => this.dialogRef.close()
        });
    }

    // Funzioni interne

    private noteChange(change: (score: ScoreDocument, id: string) => string | null | void): void {
        if (!this.selectedId) {
            this.message = this.measure?.empty ? 'La battuta è vuota: aggiungi una pausa per inserire le note' : 'Seleziona prima una nota sullo spartito';
            this.changeDetector.markForCheck();
            return;
        }
        const id = this.selectedId;
        this.apply((score) => change(score, id));
    }

    private measureChange(change: (score: ScoreDocument, index: number) => string | null | void): void {
        if (!this.measure) {
            this.message = 'Seleziona prima una battuta sullo spartito';
            this.changeDetector.markForCheck();
            return;
        }
        const index = this.measure.index;
        this.apply((score) => change(score, index));
    }

    /** Esegue una modifica; se non riesce ripristina il documento e ne mostra il motivo. */
    private apply(change: (score: ScoreDocument) => string | null | void): void {
        if (!this.score || this.saving) return;
        this.stop();
        const before = this.score.snapshot();
        try {
            const selected = change(this.score);
            this.history.record(before);
            if (typeof selected === 'string') {
                this.selectedId = selected;
                this.emptyMeasureId = null;
            }
            this.message = null;
        } catch (error) {
            this.score = ScoreDocument.restore(before, this.score.prefix);
            this.message = error instanceof EditError ? error.message : 'Modifica non riuscita';
            if (!(error instanceof EditError)) console.error(error);
        }
        this.afterChange();
    }

    private restore(snapshot: string): void {
        if (!this.score) return;
        this.stop();
        this.score = ScoreDocument.restore(snapshot, this.score.prefix);
        this.message = null;
        this.afterChange();
    }

    private afterChange(): void {
        const score = this.score!;
        this.dirty = score.snapshot() !== this.initialSnapshot;
        this.checks = score.checks();
        const selectionExists = this.selectedId ? !!score.find(this.selectedId) : !this.emptyMeasureId || !!score.find(this.emptyMeasureId);
        if (!selectionExists) {
            // La nota o la battuta selezionata non esiste più, per esempio dopo «Annulla»: si resta sulla stessa battuta.
            const index = Math.min(this.measure?.index ?? 0, score.measures().length - 1);
            this.selectedId = edits.firstNoteOf(score, index);
            this.emptyMeasureId = this.selectedId ? null : (score.measure(index).getAttribute('id') ?? null);
        }
        if (this.rangeEnd !== null && this.rangeEnd >= score.measures().length) this.rangeEnd = null;
        this.refreshSelection();
        this.changeDetector.markForCheck();
        this.scheduleRender();
    }

    /**
     * Ridisegna al fotogramma successivo: le modifiche che arrivano prima, come un tasto tenuto premuto, si disegnano una
     * volta sola. Il disegno di Verovio blocca la pagina per circa 0,2 secondi su una parte di 300 battute.
     */
    private scheduleRender(): void {
        if (this.renderFrame !== undefined) return;
        this.renderFrame = requestAnimationFrame(() => {
            this.renderFrame = undefined;
            void this.render();
        });
    }

    private select(id: string | null): void {
        this.selectedId = id;
        this.emptyMeasureId = null;
        this.message = null;
        this.refreshSelection();
        this.decorate(true);
        this.changeDetector.markForCheck();
    }

    private selectEmptyMeasure(index: number): void {
        this.selectedId = null;
        this.emptyMeasureId = this.score?.measure(index).getAttribute('id') ?? null;
        this.message = null;
        this.refreshSelection();
        this.decorate(true);
        this.changeDetector.markForCheck();
    }

    private selectAdjacent(delta: number): void {
        if (!this.score || !this.selectedId) return;
        const next = edits.adjacentNote(this.score, this.selectedId, delta);
        if (next) {
            this.rangeEnd = null;
            this.select(next);
        }
    }

    private refreshSelection(): void {
        const emptyMeasure = !this.selectedId && this.emptyMeasureId ? this.score?.find(this.emptyMeasureId) : null;
        if (!this.score || (!emptyMeasure && (!this.selectedId || !this.score.find(this.selectedId)))) {
            this.note = null;
            this.measure = null;
            this.highlight = null;
            return;
        }
        this.note = emptyMeasure ? null : edits.noteInfo(this.score, this.selectedId!);
        this.measure = edits.measureInfo(this.score, emptyMeasure ? this.score.measureIndex(emptyMeasure) : this.note!.measureIndex);
        this.timeBeats = this.measure.beats;
        this.timeBeatType = this.measure.beatType;
        this.tempo = this.measure.tempo;
        // Su una pausa di più battute il numero mostra la sua durata, da correggere; altrimenti resta l'ultimo scritto.
        if (this.measure.multiRest > 1) this.measureCount = this.measure.multiRest;
        this.updatePage();
    }

    private async render(): Promise<void> {
        const host = this.notationHost;
        if (!this.score || !host) return;
        const token = ++this.renderToken;
        const scrollTop = host.parentElement?.scrollTop ?? 0;
        this.rendering = true;
        this.changeDetector.markForCheck();
        try {
            const pages = await this.renderer.render(this.score.snapshot(), host.clientWidth || 900, EDITOR_SCALE);
            if (token !== this.renderToken) return;
            // L'SVG è generato da Verovio a partire dal MusicXML: i testi sono codificati e non contiene script.
            host.innerHTML = pages.join('');
            if (host.parentElement) host.parentElement.scrollTop = scrollTop;
            this.decorate(true);
        } catch {
            if (token === this.renderToken) this.message = 'Impossibile disegnare lo spartito dopo questa modifica: annullala';
        } finally {
            if (token === this.renderToken) {
                this.rendering = false;
                this.changeDetector.markForCheck();
            }
        }
    }

    /** Evidenzia sullo spartito la nota selezionata, la sua battuta, l'intervallo da ascoltare e le battute da verificare. */
    private decorate(scroll: boolean): void {
        const host = this.notationHost;
        if (!this.score || !host) return;
        for (const className of ['is-selected', 'is-current-measure', 'is-in-range', 'is-wrong']) {
            for (const item of Array.from(host.querySelectorAll(`.${className}`))) item.classList.remove(className);
        }
        const measures = this.score.measures();
        for (const index of this.checks.wrongDuration) this.drawnMeasure(measures, index)?.classList.add('is-wrong');
        if (this.measure) {
            const from = Math.min(this.measure.index, this.rangeEnd ?? this.measure.index);
            const to = Math.max(this.measure.index, this.rangeEnd ?? this.measure.index);
            for (let index = from; index <= to; index++) this.drawnMeasure(measures, index)?.classList.add(index === this.measure.index ? 'is-current-measure' : 'is-in-range');
        }
        const selected = this.selectedId ? host.querySelector(`[id="${this.selectedId}"]`) : null;
        selected?.classList.add('is-selected');
        if (scroll) (selected ?? (this.measure ? this.drawnMeasure(measures, this.measure.index) : null))?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
    }

    /** Battuta disegnata; le battute di una pausa di più battute usano quella che apre il gruppo. */
    private drawnMeasure(measures: Element[], index: number): Element | null {
        const host = this.notationHost;
        if (!host) return null;
        for (let current = index; current >= 0; current--) {
            const id = measures[current]?.getAttribute('id');
            const drawn = id ? host.querySelector(`[id="${id}"]`) : null;
            if (drawn) return drawn;
        }
        return null;
    }

    /** Battuta disegnata che contiene il punto indicato, in coordinate della finestra. */
    private measureAt(x: number, y: number): Element | null {
        const measures = Array.from(this.notationHost?.querySelectorAll('g.measure') ?? []);
        return (
            measures.find((measure) => {
                const box = measure.getBoundingClientRect();
                return x >= box.left && x <= box.right && y >= box.top && y <= box.bottom;
            }) ?? null
        );
    }

    /** Nota o pausa disegnata nella battuta più vicina al punto indicato, in coordinate della finestra. */
    private nearestNote(drawnMeasure: Element, x: number, y: number): string | null {
        let best: { id: string; distance: number } | null = null;
        for (const item of Array.from(drawnMeasure.querySelectorAll('g[id]'))) {
            const id = item.getAttribute('id')!;
            if (this.score?.find(id)?.tagName !== 'note') continue;
            const box = item.getBoundingClientRect();
            const distance = Math.hypot(Math.max(box.left - x, 0, x - box.right), Math.max(box.top - y, 0, y - box.bottom));
            if (!best || distance < best.distance) best = { id, distance };
        }
        return best?.id ?? null;
    }

    private get notationHost(): HTMLElement | null {
        return this.notation?.nativeElement ?? null;
    }

    private measureNumbers(indexes: number[]): string[] {
        const measures = this.score?.measures() ?? [];
        return indexes.map((index) => measures[index]?.getAttribute('number') ?? String(index + 1));
    }

    /** Mostra la pagina della battuta selezionata con il suo riquadro, se la posizione è nota. */
    private updatePage(): void {
        const box = this.measure ? this.boxesFor(this.measure.index)[0] : undefined;
        if (!box) {
            this.highlight = null;
            if (!this.pageUrl && this.pages.length) void this.showPage(this.pageIndex);
            return;
        }
        const margin = box.approximate ? { x: box.width * 0.5, y: box.height * 0.25 } : { x: 0.005, y: 0.005 };
        const left = Math.max(0, box.x - margin.x);
        const top = Math.max(0, box.y - margin.y);
        this.highlight = {
            left: left * 100,
            top: top * 100,
            width: (Math.min(1, box.x + box.width + margin.x) - left) * 100,
            height: (Math.min(1, box.y + box.height + margin.y) - top) * 100,
            approximate: box.approximate
        };
        if (box.page - 1 !== this.pageIndex || !this.pageUrl) void this.showPage(box.page - 1);
        else this.scrollToHighlight();
    }

    private boxesFor(measureIndex: number): MeasureBox[] {
        const source = this.score?.measureSources()[measureIndex];
        if (source === null || source === undefined) return [];
        return (this.editor?.layout ?? []).filter((box) => box.measure === source + 1);
    }

    private scrollToHighlight(): void {
        window.setTimeout(() => this.pageViewer?.nativeElement.querySelector('.score-editor__box')?.scrollIntoView({ block: 'nearest', inline: 'nearest' }));
    }

    private stop(): void {
        if (this.stopTimer !== undefined) window.clearTimeout(this.stopTimer);
        this.stopTimer = undefined;
        if (this.playing) this.audio.pause();
        this.playing = false;
        this.changeDetector.markForCheck();
    }
}
