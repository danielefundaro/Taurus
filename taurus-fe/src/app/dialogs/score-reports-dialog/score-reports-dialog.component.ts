import { Component, OnInit } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { finalize, first } from 'rxjs';
import { ImportsModule } from '../../imports';
import { ErrorReport } from '../../module';
import { ScorePlaybackService, ToastService, withErrorSummary } from '../../service';

export interface ScoreReportsDialogData {
    trackId: number;
    scoreId: number;
    partLabel: string;
    /** La parte ha una versione da correggere. */
    canCorrect: boolean;
}

/** Esito del dialogo: segnalazioni chiuse e, se richiesta, apertura dell'editor sulla versione corrente. */
export interface ScoreReportsDialogResult {
    changed: boolean;
    correct: boolean;
}

/** Segnalazioni d'errore di una parte, per archivisti e amministratori. */
@Component({
    selector: 'app-score-reports-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './score-reports-dialog.component.html'
})
export class ScoreReportsDialogComponent implements OnInit {
    protected readonly data: ScoreReportsDialogData;
    protected reports: ErrorReport[] = [];
    protected loading = true;
    protected closing: number | null = null;
    private changed = false;

    constructor(
        private readonly dialogRef: DynamicDialogRef<ScoreReportsDialogComponent>,
        private readonly playbackService: ScorePlaybackService,
        private readonly toastService: ToastService,
        config: DynamicDialogConfig<ScoreReportsDialogData>
    ) {
        this.data = config.data!;
    }

    ngOnInit(): void {
        this.playbackService
            .errorReports(this.data.trackId)
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe((reports) => (this.reports = reports.filter((report) => report.scoreId === this.data.scoreId)));
    }

    protected get openReports(): ErrorReport[] {
        return this.reports.filter((report) => report.status === 'OPEN');
    }

    protected get closedReports(): ErrorReport[] {
        return this.reports.filter((report) => report.status === 'CLOSED');
    }

    protected closeReport(report: ErrorReport): void {
        this.closing = report.id;
        this.playbackService
            .closeErrorReport(this.data.trackId, report.id, withErrorSummary('Segnalazione non chiusa'))
            .pipe(
                first(),
                finalize(() => (this.closing = null))
            )
            .subscribe((closed) => {
                this.changed = true;
                this.reports = this.reports.map((item) => (item.id === closed.id ? closed : item));
                this.toastService.success('Segnalazione chiusa', 'La segnalazione resta nello storico della parte.');
            });
    }

    protected correct(): void {
        this.dialogRef.close({ changed: this.changed, correct: true } satisfies ScoreReportsDialogResult);
    }

    protected close(): void {
        this.dialogRef.close({ changed: this.changed, correct: false } satisfies ScoreReportsDialogResult);
    }

    protected closureLabel(report: ErrorReport): string {
        const when = report.closedAt ? new Date(report.closedAt).toLocaleDateString('it-IT') : '';
        const who = report.closedBy ? ` da ${report.closedBy}` : '';
        return report.closure === 'APPROVAL' ? `Chiusa il ${when} con l'approvazione di una nuova versione` : `Chiusa il ${when}${who}`;
    }
}
