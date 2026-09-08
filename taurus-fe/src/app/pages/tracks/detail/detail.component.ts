import { HttpHeaders } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { DialogService } from 'primeng/dynamicdialog';
import { FileUpload } from 'primeng/fileupload';
import { delay, finalize, first, firstValueFrom } from 'rxjs';
import { TypeHandlerComponent } from '../../../components/type-handler/type-handler.component';
import { ScoreWorkspaceComponent } from './score-workspace/score-workspace.component';
import { RoleEnums, StateEnums, StateLabel, StateLabelsMap } from '../../../constants';
import { PdfManipulatorDialogComponent } from '../../../dialogs/pdf-manipulator-dialog/pdf-manipulator-dialog.component';
import { ImportsModule } from '../../../imports';
import { DetailPageBase } from '../../_shared/detail-page.base';
import { Instruments, InstrumentsCriteria, SheetsMusic, TrackUploadJob, TrackUploadJobStatus, Tracks } from '../../../module';
import { PdfAnnotations } from '../../../module/pdf-annotations.module';
import { ConfirmService, InstrumentsService, KeycloakService, PrinterService, ToastService, TracksService } from '../../../service';

@Component({
    selector: 'app-track-detail',
    imports: [ImportsModule, TypeHandlerComponent, ScoreWorkspaceComponent],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [TracksService, InstrumentsService, KeycloakService, DialogService],
    changeDetection: ChangeDetectionStrategy.Default
})
export class DetailComponent extends DetailPageBase implements OnInit {
    @ViewChild('fu') private fileUpload?: FileUpload;

    protected track: Tracks = new Tracks();
    protected autoFilteredStatesLabels: StateLabel[];
    protected RolesEnum: typeof RoleEnums = RoleEnums;
    protected readonly StateEnum: typeof StateEnums = StateEnums;
    protected readonly previewTooltip = "Aggiungi almeno una parte per abilitare l'anteprima";
    protected selectedFile: File | null = null;
    protected annotations: PdfAnnotations | null = null;
    protected uploading = false;
    protected uploadJobs: TrackUploadJob[] = [];
    protected activeTab = 'details';
    protected saveError = false;
    private detailsDirty = false;
    private scoresDirty = false;

    protected get visibleUploadJobs(): TrackUploadJob[] {
        return this.uploadJobs.slice(0, 5);
    }

    protected instruments: Instruments[];

    constructor(
        private readonly tracksService: TracksService,
        private readonly instrumentsService: InstrumentsService,
        private readonly printerService: PrinterService,
        private readonly keycloakService: KeycloakService,
        private readonly toastService: ToastService,
        private readonly routeService: ActivatedRoute,
        private readonly router: Router,
        private readonly confirmService: ConfirmService,
        private readonly dialogService: DialogService
    ) {
        super();
        this.instruments = [];
        this.autoFilteredStatesLabels = StateLabelsMap;
    }

    ngOnInit() {
        const requestedTab = this.routeService.snapshot?.queryParamMap?.get('tab');
        if (requestedTab && ['details', 'parts', 'pdf'].includes(requestedTab)) this.activeTab = requestedTab;
        this.routeService.params.pipe(first()).subscribe((params) => {
            const trackId = params['id'];
            this.loadElement(trackId);
            this.loadUploadJobs(trackId);
        });

        let page = 0;
        const instrumentsCriteria: InstrumentsCriteria = { page: page, sort: ['name,asc'] };

        this.instrumentsService
            .getAll(instrumentsCriteria)
            .pipe(first())
            .subscribe(async (result) => {
                let totalElements = result.totalElements;
                this.instruments = result.content;

                while (totalElements > this.instruments.length) {
                    instrumentsCriteria.page = ++page;

                    const data = await firstValueFrom(this.instrumentsService.getAll(instrumentsCriteria));
                    this.instruments.push(...data.content);
                    totalElements = data.totalElements;
                }
            });
    }

    protected get isUser(): boolean {
        return this.keycloakService.isUser || this.keycloakService.isUserExternal;
    }

    protected confirmDelete(): void {
        this.confirmService.confirmDestructive({
            title: 'Elimina traccia',
            consequence: `La traccia “${this.track.name || 'Senza nome'}” non sarà più visibile né disponibile negli elenchi.`,
            actionLabel: 'Elimina definitivamente',
            accept: () => {
                this.tracksService
                    .delete(this.track.id)
                    .pipe(first())
                    .subscribe({
                        next: () => {
                            this.clearDirtyUnits();
                            this.toastService.success('Traccia eliminata', 'La traccia non è più visibile.');
                            this.router.navigate(['/tracks']);
                        }
                    });
            }
        });
    }

    protected save(): void {
        this.normalizeTrackScores();
        this.saving = true;
        this.saveError = false;
        this.tracksService
            .update(this.track.id, this.track)
            .pipe(
                delay(1000),
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe({
                next: (track: Tracks) => {
                    this.detailsDirty = false;
                    this.scoresDirty = false;
                    this.clearDirtyUnits();
                    this.toastService.success('Traccia aggiornata', 'Le modifiche sono state salvate.');
                    this.track = track;
                },
                error: () => {
                    this.saveError = true;
                    this.toastService.error('Salvataggio non riuscito', 'La bozza è ancora disponibile. Correggi l’errore o riprova.');
                }
            });
    }

    protected onTabChange(tab: string | number | undefined): void {
        if (typeof tab !== 'string') return;
        this.activeTab = tab;
        this.router.navigate([], { relativeTo: this.routeService, queryParams: { tab }, queryParamsHandling: 'merge', replaceUrl: true });
    }

    protected onScoresChange(scores: SheetsMusic[]): void {
        this.track.scores = scores;
        this.saveError = false;
    }

    protected onScoresDirtyChange(dirty: boolean): void {
        this.scoresDirty = dirty;
        this.syncFormDirty();
    }

    protected preview(): void {
        this.printerService.preview(this.track);
    }

    protected filterStates(event: AutoCompleteCompleteEvent) {
        this.autoFilteredStatesLabels = StateLabelsMap.filter((state) => (state.name.toLowerCase().includes(event.query.toLowerCase()) ? state : null)).filter((state) => state !== null) as StateLabel[];
    }

    protected trackStream(): string {
        return this.tracksService.stream(this.track.id);
    }

    protected httpHeaders(): HttpHeaders {
        return new HttpHeaders({ Authorization: `Bearer ${this.keycloakService.token}` });
    }

    protected onTypeChange(types: string[]): void {
        this.track!.type = types;
        this.markDetailsDirty();
    }

    protected markDetailsDirty(): void {
        this.detailsDirty = true;
        this.syncFormDirty();
    }

    protected onUploadError(): void {
        this.toastService.error('Caricamento non riuscito', 'Il file non è stato aggiunto. Riprova.');
    }

    protected onFileSelect(event: any): void {
        this.selectedFile = event.currentFiles?.[0] ?? event.files?.[0] ?? null;
        this.annotations = null;
    }

    protected onFileClear(): void {
        this.selectedFile = null;
        this.annotations = null;
    }

    protected openManipulator(): void {
        if (!this.selectedFile) return;
        const ref = this.dialogService.open(PdfManipulatorDialogComponent, {
            header: 'Manipolazione PDF',
            width: '90vw',
            height: '90vh',
            focusTrap: false,
            focusOnShow: false,
            data: { file: this.selectedFile },
            contentStyle: {
                overflow: 'hidden',
                padding: '0',
                display: 'flex',
                flexDirection: 'column',
                height: 'calc(90vh - 54px)'
            }
        });
        ref.onClose.pipe(first()).subscribe((result: PdfAnnotations | null | undefined) => {
            if (result !== null && result !== undefined) {
                this.annotations = result;
            }
        });
    }

    protected handleUpload(event: any): void {
        const file: File = event.files?.[0] ?? this.selectedFile;
        if (!file) return;

        this.uploading = true;
        const formData = new FormData();
        formData.append('file', file);
        if (this.annotations && (this.annotations.excludedPages.length > 0 || this.annotations.cropRegions.length > 0)) {
            formData.append('annotations', JSON.stringify(this.annotations));
        }

        this.tracksService.uploadPdf(formData, this.track.id).subscribe({
            next: (job) => {
                this.uploading = false;
                this.selectedFile = null;
                this.annotations = null;
                this.fileUpload?.clear();
                this.uploadJobs = [job, ...this.uploadJobs.filter((value) => value.id !== job.id)];
                this.toastService.success('PDF ricevuto', 'Il file è stato aggiunto alla coda di elaborazione.');
            },
            error: () => {
                this.uploading = false;
                this.onUploadError();
            }
        });
    }

    protected uploadStatusLabel(status: TrackUploadJobStatus): string {
        return {
            TO_PROCESS: 'In coda',
            IN_PROGRESS: 'Elaborazione in corso',
            DONE: 'Completato',
            ERROR: 'Elaborazione non riuscita',
            NOT_FOUND: 'File sorgente non disponibile'
        }[status];
    }

    protected isPendingUpload(job: TrackUploadJob): boolean {
        return job.status === 'TO_PROCESS' || job.status === 'IN_PROGRESS';
    }

    protected retryUpload(job: TrackUploadJob): void {
        this.tracksService
            .retryUploadJob(this.track.id, job.id)
            .pipe(first())
            .subscribe((queued) => {
                this.uploadJobs = this.uploadJobs.map((value) => (value.id === queued.id ? queued : value));
                this.toastService.success('Elaborazione riavviata', 'Il PDF è stato rimesso in coda.');
            });
    }

    protected get hasAnnotations(): boolean {
        return !!(this.annotations && (this.annotations.excludedPages.length > 0 || this.annotations.cropRegions.length > 0));
    }

    private loadElement(id: number | string) {
        this.loading = true;
        this.tracksService
            .getById(Number(id))
            .pipe(
                first(),
                finalize(() => (this.loading = false))
            )
            .subscribe((track) => {
                this.track = track;
                this.detailsDirty = false;
                this.scoresDirty = false;
                this.clearDirtyUnits();
                this.saveError = false;
            });
    }

    private normalizeTrackScores(): void {
        (this.track.scores ?? []).forEach((score, scoreIndex) => {
            score.order = scoreIndex + 1;
            (score.media ?? []).forEach((media, mediaIndex) => (media.order = mediaIndex + 1));
            (score.instruments ?? []).forEach((instrument, instrumentIndex) => (instrument.order = instrumentIndex + 1));
        });
    }

    private syncFormDirty(): void {
        this.isDirty = this.detailsDirty || this.scoresDirty;
    }

    private loadUploadJobs(trackId: number): void {
        this.tracksService
            .getUploadJobs(trackId)
            .pipe(first())
            .subscribe((jobs) => (this.uploadJobs = jobs));
    }
}
