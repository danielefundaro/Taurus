import { Component } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { finalize, first } from 'rxjs';
import { ImportsModule } from '../../imports';
import { MusicXmlPart, ScoreVersion } from '../../module';
import { ScorePlaybackService, withErrorSummary } from '../../service';

export interface ScoreUploadDialogData {
    trackId: number;
    scoreId: number;
    partLabel: string;
    /** Il servizio di riconoscimento converte anche i file MIDI. */
    midi: boolean;
}

/** Caricamento di un file MusicXML, o MIDI convertito dal servizio di riconoscimento, come nuova versione di una parte. */
@Component({
    selector: 'app-score-upload-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './score-upload-dialog.component.html'
})
export class ScoreUploadDialogComponent {
    protected readonly data: ScoreUploadDialogData;
    protected file: File | null = null;
    protected parts: MusicXmlPart[] = [];
    protected partId: string | null = null;
    protected approve = false;
    protected inspecting = false;
    protected saving = false;

    constructor(
        private readonly dialogRef: DynamicDialogRef<ScoreUploadDialogComponent>,
        private readonly playbackService: ScorePlaybackService,
        config: DynamicDialogConfig<ScoreUploadDialogData>
    ) {
        this.data = config.data!;
    }

    protected get intro(): string {
        const musicXml = 'Il file diventa la versione ascoltata per la parte. Sono accettati file MusicXML (.musicxml, .xml, .mxl), per esempio esportati da MuseScore';
        return this.data.midi ? `${musicXml}, e MIDI (.mid, .midi): un MIDI viene convertito con le durate arrotondate e va verificato.` : `${musicXml}.`;
    }

    protected get invalidCount(): number {
        return (this.file ? 0 : 1) + (this.parts.length > 1 && !this.partId ? 1 : 0);
    }

    protected partLabel(part: MusicXmlPart): string {
        return `${part.name || part.id} · ${part.measureCount} ${part.measureCount === 1 ? 'battuta' : 'battute'}`;
    }

    protected onFileChange(event: Event): void {
        const input = event.target as HTMLInputElement;
        this.file = input.files?.[0] ?? null;
        this.parts = [];
        this.partId = null;
        if (!this.file) return;
        this.inspecting = true;
        this.playbackService
            .inspect(this.data.trackId, this.data.scoreId, this.file, withErrorSummary('File non valido'))
            .pipe(
                first(),
                finalize(() => (this.inspecting = false))
            )
            .subscribe({
                next: (parts) => {
                    this.parts = parts;
                    this.partId = parts.length === 1 ? parts[0].id : null;
                },
                error: () => {
                    this.file = null;
                    input.value = '';
                }
            });
    }

    protected save(): void {
        if (this.invalidCount || this.inspecting || !this.file) return;
        this.saving = true;
        this.playbackService
            .upload(this.data.trackId, this.data.scoreId, this.file, this.partId, this.approve, withErrorSummary('Caricamento non riuscito'))
            .pipe(
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe((version: ScoreVersion) => this.dialogRef.close(version));
    }

    protected cancel(): void {
        this.dialogRef.close();
    }
}
