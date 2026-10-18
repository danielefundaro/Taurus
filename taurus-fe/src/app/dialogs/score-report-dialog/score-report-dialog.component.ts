import { Component } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { finalize, first } from 'rxjs';
import { ImportsModule } from '../../imports';
import { ScorePlaybackService, withErrorSummary } from '../../service';

/** Una parte che si può segnalare, con i numeri delle sue battute. */
export interface ReportablePart {
    scoreId: number;
    label: string;
    measures: string[];
}

export interface ScoreReportDialogData {
    trackId: number;
    parts: ReportablePart[];
    /** Parte proposta, per esempio quella mostrata nel lettore. */
    scoreId: number;
    /** Battuta proposta, per esempio quella in ascolto. */
    measureNumber: string | null;
}

export const MAX_REPORT_LENGTH = 1000;

/** Segnalazione di un errore sentito durante l'ascolto, per archivisti e amministratori. */
@Component({
    selector: 'app-score-report-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './score-report-dialog.component.html'
})
export class ScoreReportDialogComponent {
    protected readonly data: ScoreReportDialogData;
    protected readonly maxLength = MAX_REPORT_LENGTH;
    protected scoreId: number;
    protected measureNumber: string | null;
    protected message = '';
    protected saving = false;

    constructor(
        private readonly dialogRef: DynamicDialogRef<ScoreReportDialogComponent>,
        private readonly playbackService: ScorePlaybackService,
        config: DynamicDialogConfig<ScoreReportDialogData>
    ) {
        this.data = config.data!;
        this.scoreId = this.data.scoreId;
        this.measureNumber = this.measureOptions.some((option) => option.value === this.data.measureNumber) ? this.data.measureNumber : null;
    }

    protected get measureOptions(): { label: string; value: string }[] {
        const part = this.data.parts.find((candidate) => candidate.scoreId === this.scoreId);
        return Array.from(new Set(part?.measures ?? [])).map((number) => ({ label: `Battuta ${number}`, value: number }));
    }

    protected get invalidCount(): number {
        return this.message.trim() ? 0 : 1;
    }

    protected changePart(scoreId: number): void {
        this.scoreId = scoreId;
        if (!this.measureOptions.some((option) => option.value === this.measureNumber)) this.measureNumber = null;
    }

    protected send(): void {
        if (this.invalidCount || this.saving) return;
        this.saving = true;
        this.playbackService
            .reportError(this.data.trackId, this.scoreId, { measureNumber: this.measureNumber, message: this.message.trim() }, withErrorSummary('Segnalazione non inviata'))
            .pipe(
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe(() => this.dialogRef.close(true));
    }

    protected cancel(): void {
        this.dialogRef.close(false);
    }
}
