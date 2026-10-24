import { Component, OnInit } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { finalize, first } from 'rxjs';
import { ImportsModule } from '../../imports';
import { PartPlaybackSettings, PlaybackInstrument, ScoreVersion } from '../../module';
import { GENERAL_MIDI_FAMILIES, programName } from '../../score-player/general-midi';
import { checkIssues, originLabel } from '../../score-player/playback-labels';
import { ConfirmService, ScorePlaybackService, ToastService } from '../../service';

export interface ScoreVersionsDialogData {
    trackId: number;
    scoreId: number;
    partLabel: string;
    settings: PartPlaybackSettings;
    /** Primo strumento della parte: con i campi vuoti la parte usa il suo suono e la sua trasposizione. */
    instrument?: PlaybackInstrument | null;
}

/** Esito del dialogo: versioni o suono cambiati e, se richiesta, la versione da aprire nell'editor di correzione. */
export interface ScoreVersionsDialogResult {
    changed: boolean;
    correctVersionId?: number;
}

/** Versioni MusicXML di una parte e sovrascritture di suono e trasposizione. */
@Component({
    selector: 'app-score-versions-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './score-versions-dialog.component.html'
})
export class ScoreVersionsDialogComponent implements OnInit {
    protected readonly data: ScoreVersionsDialogData;
    protected readonly soundGroups = GENERAL_MIDI_FAMILIES.map((family) => ({ label: family.label, items: family.sounds.map((sound) => ({ label: `${sound.program} · ${sound.name}`, value: sound.program })) }));
    protected versions: ScoreVersion[] = [];
    protected loading = true;
    protected busy = false;
    protected transpositionOverride: number | null;
    protected soundProgramOverride: number | null;
    protected soundBankOverride: number | null;
    private changed = false;
    /** Suono salvato sulla parte, per riconoscere i campi modificati e non ancora salvati. */
    private savedSound: PartPlaybackSettings;

    constructor(
        private readonly dialogRef: DynamicDialogRef<ScoreVersionsDialogComponent>,
        private readonly playbackService: ScorePlaybackService,
        private readonly toastService: ToastService,
        private readonly confirmService: ConfirmService,
        config: DynamicDialogConfig<ScoreVersionsDialogData>
    ) {
        this.data = config.data!;
        this.transpositionOverride = this.data.settings.transpositionOverride ?? null;
        this.soundProgramOverride = this.data.settings.soundProgramOverride ?? null;
        this.soundBankOverride = this.data.settings.soundBankOverride ?? null;
        this.savedSound = this.soundSettings();
    }

    /** I campi del suono differiscono da quelli salvati: chiudere il dialogo li perderebbe. */
    protected get soundDirty(): boolean {
        const current = this.soundSettings();
        return (
            current.transpositionOverride !== this.savedSound.transpositionOverride ||
            current.soundProgramOverride !== this.savedSound.soundProgramOverride ||
            current.soundBankOverride !== this.savedSound.soundBankOverride
        );
    }

    /** Nome dello strumento da cui la parte eredita suono e trasposizione. */
    protected get instrumentName(): string {
        return this.data.instrument?.name ?? 'suo primo strumento';
    }

    /** Valori ereditati dallo strumento, mostrati nei campi vuoti. */
    protected get inheritedTransposition(): string {
        const instrument = this.data.instrument;
        if (instrument?.percussionKey != null) return 'Batteria, nessuna trasposizione';
        const value = instrument?.transpositionSemitones;
        if (value == null) return 'Non configurata nello strumento';
        return `${value > 0 ? '+' : ''}${value} dallo strumento`;
    }

    protected get inheritedSound(): string {
        const instrument = this.data.instrument;
        if (instrument?.percussionKey != null) return 'Come lo strumento: batteria';
        const name = programName(instrument?.soundProgram);
        return name ? `Come lo strumento: ${name}` : 'Come lo strumento: non configurato';
    }

    protected get inheritedBank(): string {
        if (this.soundProgramOverride != null) return '';
        const bank = this.data.instrument?.soundBank;
        return bank == null ? '' : `${bank} dallo strumento`;
    }

    ngOnInit(): void {
        this.load();
    }

    protected originLabel = originLabel;

    protected issues(version: ScoreVersion): string[] {
        return checkIssues(version.checks);
    }

    protected use(version: ScoreVersion): void {
        this.run(this.playbackService.makeCurrent(this.data.trackId, this.data.scoreId, version.id), 'Versione in ascolto', `La versione ${version.versionNumber} è quella ascoltata per la parte.`);
    }

    protected approve(version: ScoreVersion): void {
        this.run(this.playbackService.approve(this.data.trackId, this.data.scoreId, version.id), 'Versione approvata', `La versione ${version.versionNumber} è approvata e ascoltata per la parte.`);
    }

    protected download(version: ScoreVersion): void {
        this.playbackService
            .versionFile(this.data.trackId, this.data.scoreId, version.id)
            .pipe(first())
            .subscribe((blob) => {
                const url = URL.createObjectURL(blob);
                const link = document.createElement('a');
                link.href = url;
                link.download = `${this.data.partLabel.replace(/[^\p{L}\p{N} _-]+/gu, '').trim() || 'parte'} - versione ${version.versionNumber}.mxl`;
                link.click();
                URL.revokeObjectURL(url);
            });
    }

    protected saveSound(): void {
        this.busy = true;
        const settings = this.soundSettings();
        this.playbackService
            .updatePartSettings(this.data.trackId, this.data.scoreId, settings)
            .pipe(
                first(),
                finalize(() => (this.busy = false))
            )
            .subscribe(() => {
                this.changed = true;
                this.savedSound = settings;
                this.toastService.success('Suono della parte salvato', 'La parte suonerà con le nuove impostazioni.');
            });
    }

    protected resetSound(): void {
        this.transpositionOverride = null;
        this.soundProgramOverride = null;
        this.soundBankOverride = null;
        this.saveSound();
    }

    /** Chiude il dialogo e apre la versione nell'editor di correzione; con il suono non salvato chiede prima. */
    protected correct(version: ScoreVersion): void {
        const open = (): void => this.dialogRef.close({ changed: this.changed, correctVersionId: version.id } satisfies ScoreVersionsDialogResult);
        if (!this.soundDirty) {
            open();
            return;
        }
        this.confirmService.confirmDiscard({
            title: 'Modifiche non salvate',
            consequence: 'Il suono della parte non è stato salvato: aprendo l’editor le modifiche andranno perse.',
            actionLabel: 'Apri senza salvare',
            accept: open
        });
    }

    protected close(): void {
        this.dialogRef.close({ changed: this.changed } satisfies ScoreVersionsDialogResult);
    }

    private soundSettings(): PartPlaybackSettings {
        return {
            transpositionOverride: this.transpositionOverride ?? null,
            soundProgramOverride: this.soundProgramOverride ?? null,
            soundBankOverride: this.soundProgramOverride == null ? null : (this.soundBankOverride ?? null)
        };
    }

    private run(request: ReturnType<ScorePlaybackService['approve']>, title: string, detail: string): void {
        this.busy = true;
        request
            .pipe(
                first(),
                finalize(() => (this.busy = false))
            )
            .subscribe(() => {
                this.changed = true;
                this.toastService.success(title, detail);
                this.load();
            });
    }

    private load(): void {
        this.loading = true;
        this.playbackService
            .versions(this.data.trackId, this.data.scoreId)
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe((versions) => (this.versions = versions));
    }
}
