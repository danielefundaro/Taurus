import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { DialogService } from 'primeng/dynamicdialog';
import { finalize, first, Subscription, timer } from 'rxjs';
import { ScoreEditorDialogComponent, ScoreEditorDialogData } from '../../../../dialogs/score-editor-dialog/score-editor-dialog.component';
import { ScorePlayerDialogComponent, ScorePlayerDialogData } from '../../../../dialogs/score-player-dialog/score-player-dialog.component';
import { ScoreReportsDialogComponent, ScoreReportsDialogData, ScoreReportsDialogResult } from '../../../../dialogs/score-reports-dialog/score-reports-dialog.component';
import { ScoreUploadDialogComponent, ScoreUploadDialogData } from '../../../../dialogs/score-upload-dialog/score-upload-dialog.component';
import { ScoreVersionsDialogComponent, ScoreVersionsDialogData, ScoreVersionsDialogResult } from '../../../../dialogs/score-versions-dialog/score-versions-dialog.component';
import { ImportsModule } from '../../../../imports';
import { PartPlayback, RecognitionJob, ScoreVersion, TrackPlayback } from '../../../../module';
import { drumName, programName } from '../../../../score-player/general-midi';
import { checksSummary, isRecognitionActive, partLabel, recognitionLabel } from '../../../../score-player/playback-labels';
import { ScorePlaybackService, ToastService, withErrorSummary } from '../../../../service';

/** Intervallo di aggiornamento dello stato mentre un riconoscimento è in coda o in corso. */
export const RECOGNITION_POLL_MILLIS = 5000;

/**
 * Ascolto delle parti di una traccia: disponibilità per parte, apertura del lettore e, per archivisti e amministratori,
 * riconoscimento delle pagine, caricamento dei file MusicXML o MIDI, correzione, versioni, segnalazioni d'errore, suono
 * delle parti e impostazioni della partitura.
 */
@Component({
    selector: 'app-score-playback-panel',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './score-playback-panel.component.html',
    providers: [DialogService]
})
export class ScorePlaybackPanelComponent implements OnChanges, OnDestroy {
    @Input({ required: true }) trackId!: number;
    @Input() trackDirty = false;
    @Output() trackVersionChange = new EventEmitter<number>();

    protected playback?: TrackPlayback;
    protected loading = false;
    protected savingSettings = false;
    protected starting = false;
    protected tempoBpm: number | null = null;
    protected referenceScoreId: number | null = null;
    private polling?: Subscription;

    constructor(
        protected readonly playbackService: ScorePlaybackService,
        private readonly dialogService: DialogService,
        private readonly toastService: ToastService
    ) {}

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['trackId'] && this.trackId) this.reload();
    }

    ngOnDestroy(): void {
        this.polling?.unsubscribe();
    }

    protected get canManage(): boolean {
        return this.playbackService.canManage;
    }

    protected get anyAvailable(): boolean {
        return !!this.playback?.parts.some((part) => part.availability === 'AVAILABLE');
    }

    /** Il servizio di riconoscimento è disponibile per chi gestisce la traccia. */
    protected get canRecognize(): boolean {
        return this.canManage && !!this.playback?.recognitionAvailable;
    }

    /** Parti con pagine e senza un riconoscimento in corso. */
    protected get recognizableParts(): PartPlayback[] {
        return this.playback?.parts.filter((part) => part.hasPages && !isRecognitionActive(part.recognition)) ?? [];
    }

    protected label = partLabel;
    protected summary = checksSummary;
    protected recognitionStatus = recognitionLabel;
    protected recognizing = isRecognitionActive;

    protected soundLabel(part: PartPlayback): string {
        const sound = part.sound;
        if (sound.percussionKeys.length) return `Batteria: ${sound.percussionKeys.map((key) => drumName(key)).join(', ')}`;
        if (!sound.configured) {
            const names = part.instruments.map((instrument) => instrument.name).join(', ');
            return names ? `Suono e trasposizione non configurati per ${names}` : 'Suono e trasposizione non configurati';
        }
        const transposition = sound.transpositionSemitones ?? 0;
        return `${programName(sound.soundProgram)} · ${transposition === 0 ? 'in suoni reali' : `${transposition > 0 ? '+' : ''}${transposition} semitoni`}`;
    }

    /** Per chi gestisce la traccia, «Ascolta» su una parte senza versione avvia il riconoscimento. */
    protected listenStartsRecognition(part: PartPlayback): boolean {
        return this.canRecognize && part.availability !== 'AVAILABLE' && part.hasPages && !isRecognitionActive(part.recognition);
    }

    protected listenHint(part: PartPlayback): string {
        if (part.availability === 'AVAILABLE') return '';
        return this.listenStartsRecognition(part) ? "Avvia il riconoscimento delle pagine: l'ascolto sarà disponibile al termine" : 'Ascolto non ancora disponibile';
    }

    protected listen(part: PartPlayback): void {
        if (this.listenStartsRecognition(part)) {
            this.recognize(part);
            return;
        }
        this.openPlayer('part', part);
    }

    protected openPlayer(mode: 'part' | 'score', part?: PartPlayback): void {
        if (!this.playback) return;
        const data: ScorePlayerDialogData = { trackId: this.trackId, playback: this.playback, mode, scoreId: part?.scoreId };
        this.dialogService.open(ScorePlayerDialogComponent, {
            header: mode === 'score' ? `Partitura · ${this.playback.trackName ?? ''}` : `${partLabel(part!)} · ${this.playback.trackName ?? ''}`,
            width: '96vw',
            height: '94vh',
            modal: true,
            closable: true,
            maximizable: false,
            focusOnShow: false,
            data,
            contentStyle: { padding: '0', overflow: 'hidden', height: 'calc(94vh - 60px)' }
        });
    }

    protected recognize(part: PartPlayback): void {
        this.start(this.playbackService.recognize(this.trackId, part.scoreId, withErrorSummary('Riconoscimento non avviato')), `«${partLabel(part)}»: l'ascolto sarà disponibile al termine del riconoscimento.`);
    }

    protected recognizeAll(): void {
        this.start(this.playbackService.recognizeAll(this.trackId, withErrorSummary('Riconoscimento non avviato')), 'Riceverai una notifica quando tutte le parti saranno riconosciute.');
    }

    protected upload(part: PartPlayback): void {
        const data: ScoreUploadDialogData = { trackId: this.trackId, scoreId: part.scoreId, partLabel: partLabel(part), midi: !!this.playback?.recognitionAvailable };
        const ref = this.dialogService.open(ScoreUploadDialogComponent, { showHeader: false, width: 'min(40rem, 95vw)', modal: true, focusOnShow: false, data });
        ref.onClose.pipe(first()).subscribe((version: ScoreVersion | undefined) => {
            if (!version) return;
            this.toastService.success('File caricato', `La versione ${version.versionNumber} è ora quella ascoltata per la parte.`);
            this.reload();
        });
    }

    protected versions(part: PartPlayback): void {
        const data: ScoreVersionsDialogData = {
            trackId: this.trackId,
            scoreId: part.scoreId,
            partLabel: partLabel(part),
            settings: part.settings ?? { transpositionOverride: null, soundBankOverride: null, soundProgramOverride: null }
        };
        const ref = this.dialogService.open(ScoreVersionsDialogComponent, { showHeader: false, width: 'min(56rem, 95vw)', modal: true, focusOnShow: false, data });
        ref.onClose.pipe(first()).subscribe((result: ScoreVersionsDialogResult | undefined) => {
            if (result?.changed) this.reload();
            if (result?.correctVersionId) this.correct(part, result.correctVersionId);
        });
    }

    /** Apre l'editor di correzione su una versione della parte, di norma quella ascoltata. */
    protected correct(part: PartPlayback, versionId = part.version?.id): void {
        if (!versionId) return;
        const data: ScoreEditorDialogData = { trackId: this.trackId, scoreId: part.scoreId, versionId, partLabel: partLabel(part), sound: part.sound };
        const ref = this.dialogService.open(ScoreEditorDialogComponent, {
            showHeader: false,
            width: '100vw',
            height: '100vh',
            modal: true,
            closeOnEscape: false,
            focusOnShow: false,
            focusTrap: false,
            data,
            style: { 'max-height': '100vh', 'border-radius': '0' },
            contentStyle: { padding: '0', overflow: 'hidden', height: '100vh' }
        });
        ref.onClose.pipe(first()).subscribe((version: ScoreVersion | undefined) => {
            if (!version) return;
            if (version.status === 'APPROVED') {
                this.toastService.success('Correzione approvata', `La versione ${version.versionNumber} è approvata e ascoltata per la parte; le segnalazioni aperte sono chiuse.`);
            } else {
                this.toastService.success('Correzione salvata', `La versione ${version.versionNumber}, da verificare, è ora quella ascoltata per la parte.`);
            }
            this.reload();
        });
    }

    protected reports(part: PartPlayback): void {
        const data: ScoreReportsDialogData = { trackId: this.trackId, scoreId: part.scoreId, partLabel: partLabel(part), canCorrect: !!part.version };
        const ref = this.dialogService.open(ScoreReportsDialogComponent, { showHeader: false, width: 'min(48rem, 95vw)', modal: true, focusOnShow: false, data });
        ref.onClose.pipe(first()).subscribe((result: ScoreReportsDialogResult | undefined) => {
            if (result?.changed) this.reload();
            if (result?.correct) this.correct(part);
        });
    }

    protected saveSettings(): void {
        if (!this.playback) return;
        this.savingSettings = true;
        this.playbackService
            .updateTrackSettings(this.trackId, { tempoBpm: this.tempoBpm, referenceScoreId: this.referenceScoreId, version: this.playback.version }, withErrorSummary('Impostazioni non salvate'))
            .pipe(
                first(),
                finalize(() => (this.savingSettings = false))
            )
            .subscribe((settings) => {
                this.toastService.success('Impostazioni salvate', "Tempo e parte di riferimento valgono per l'ascolto della partitura.");
                if (settings.version !== null) this.trackVersionChange.emit(settings.version);
                this.reload();
            });
    }

    reload(): void {
        this.loading = true;
        this.playbackService
            .playback(this.trackId)
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe((playback) => {
                this.playback = playback;
                this.tempoBpm = playback.tempoBpm ?? null;
                this.referenceScoreId = playback.referenceScoreId ?? null;
                this.watchRecognitions();
            });
    }

    private start(request: ReturnType<ScorePlaybackService['recognize']>, detail: string): void {
        this.starting = true;
        request
            .pipe(
                first(),
                finalize(() => (this.starting = false))
            )
            .subscribe((jobs) => {
                this.toastService.success(jobs.length === 1 ? 'Riconoscimento avviato' : `Riconoscimento di ${jobs.length} parti avviato`, detail);
                this.applyRecognitions(jobs);
            });
    }

    /** Aggiorna lo stato dei riconoscimenti; quando uno termina ricarica le parti, che hanno nuove versioni. */
    private applyRecognitions(jobs: RecognitionJob[]): void {
        if (!this.playback) return;
        let finished = false;
        const byPart = new Map(jobs.map((job) => [job.scoreId, job]));
        const parts = this.playback.parts.map((part) => {
            const job = byPart.get(part.scoreId);
            if (!job) return part;
            if (isRecognitionActive(part.recognition) && !isRecognitionActive(job)) finished = true;
            return { ...part, recognition: job };
        });
        this.playback = { ...this.playback, parts };
        if (finished) {
            this.reload();
            return;
        }
        this.watchRecognitions();
    }

    private watchRecognitions(): void {
        const active = !!this.playback?.parts.some((part) => isRecognitionActive(part.recognition));
        if (!active) {
            this.polling?.unsubscribe();
            this.polling = undefined;
            return;
        }
        if (this.polling) return;
        this.polling = timer(RECOGNITION_POLL_MILLIS).subscribe(() => {
            this.polling = undefined;
            this.playbackService
                .recognitions(this.trackId)
                .pipe(first())
                // Dopo un errore l'avanzamento non viene più seguito: riprende al prossimo caricamento della scheda.
                .subscribe((jobs) => this.applyRecognitions(jobs));
        });
    }
}
