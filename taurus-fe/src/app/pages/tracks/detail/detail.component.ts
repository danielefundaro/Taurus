import { HttpHeaders } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { FileUpload } from 'primeng/fileupload';
import { Table } from 'primeng/table';
import { delay, finalize, first, firstValueFrom } from 'rxjs';
import { TypeHandlerComponent } from '../../../components/type-handler/type-handler.component';
import { RoleEnums, StateEnums, StateLabel, StateLabelsMap } from '../../../constants';
import { EditScoreDialogComponent } from '../../../dialogs/edit-score-dialog/edit-score-dialog.component';
import { PdfManipulatorDialogComponent } from '../../../dialogs/pdf-manipulator-dialog/pdf-manipulator-dialog.component';
import { ImportsModule } from '../../../imports';
import { DetailPageBase } from '../../_shared/detail-page.base';
import { ChildrenEntities, Instruments, InstrumentsCriteria, SheetsMusic, TrackUploadJob, TrackUploadJobStatus, Tracks } from '../../../module';
import { PdfAnnotations } from '../../../module/pdf-annotations.module';
import { ConfirmService, InstrumentsService, KeycloakService, MediaService, PrinterService, ToastService, TracksService } from '../../../service';

@Component({
    selector: 'app-track-detail',
    imports: [ImportsModule, TypeHandlerComponent],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [TracksService, InstrumentsService, KeycloakService, DialogService],
    changeDetection: ChangeDetectionStrategy.Default
})
export class DetailComponent extends DetailPageBase implements OnInit {
    @ViewChild('fu') private fileUpload?: FileUpload;

    protected track: Tracks = new Tracks();
    protected cols: string[];
    protected selectedScores: SheetsMusic[];
    protected images: string[];
    protected displayGalleria: boolean;
    protected autoFilteredStatesLabels: StateLabel[];
    protected RolesEnum: typeof RoleEnums = RoleEnums;
    protected readonly StateEnum: typeof StateEnums = StateEnums;
    protected readonly previewTooltip = "Aggiungi almeno una parte per abilitare l'anteprima";
    protected responsiveOptions: any[] = [
        {
            breakpoint: '1024px',
            numVisible: 5
        },
        {
            breakpoint: '960px',
            numVisible: 4
        },
        {
            breakpoint: '768px',
            numVisible: 3
        }
    ];

    protected selectedFile: File | null = null;
    protected annotations: PdfAnnotations | null = null;
    protected uploading = false;
    protected uploadJobs: TrackUploadJob[] = [];

    protected get visibleUploadJobs(): TrackUploadJob[] {
        return this.uploadJobs.slice(0, 5);
    }

    private instruments: Instruments[];

    constructor(
        private readonly tracksService: TracksService,
        private readonly mediaService: MediaService,
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
        this.cols = ['Ordine', 'Media', 'Strumenti'];
        this.selectedScores = [];
        this.images = [];
        this.displayGalleria = false;
        this.instruments = [];
        this.autoFilteredStatesLabels = StateLabelsMap;
    }

    ngOnInit() {
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
        return this.keycloakService.isUser;
    }

    protected confirmDelete(): void {
        this.confirmService.confirmDestructive({
            title: 'Elimina traccia',
            consequence: 'La traccia verrà eliminata definitivamente.',
            actionLabel: 'Elimina',
            accept: () => {
                this.tracksService
                    .delete(this.track.id)
                    .pipe(first())
                    .subscribe({
                        next: () => {
                            this.isDirty = false;
                            this.toastService.success('Traccia eliminata', 'La traccia non è più visibile.');
                            this.router.navigate(['/tracks']);
                        }
                    });
            }
        });
    }

    protected save(): void {
        this.saving = true;
        this.tracksService
            .update(this.track.id, this.track)
            .pipe(
                delay(1000),
                first(),
                finalize(() => (this.saving = false))
            )
            .subscribe({
                next: (track: Tracks) => {
                    this.isDirty = false;
                    this.toastService.success('Traccia aggiornata', 'Le modifiche sono state salvate.');
                    this.loadElement(track.id);
                }
            });
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
        this.isDirty = true;
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

    protected confirmDeleteSelectedScores(): void {
        this.confirmService.confirmDestructive({
            title: 'Rimuovi parti',
            consequence: 'Le parti selezionate verranno rimosse dalla traccia.',
            actionLabel: 'Rimuovi',
            accept: () => this.deleteSelectedScores()
        });
    }

    protected deleteSelectedScores(): void {
        for (let selectedScore of this.selectedScores) {
            this.deleteScore(selectedScore);
        }
        this.selectedScores = [];
    }

    protected confirmMergeSelectedScores(): void {
        this.confirmService.confirmReversible({
            title: 'Unisci parti',
            consequence: `Le ${this.selectedScores.length} parti selezionate verranno unite e i media concatenati nell’ordine delle righe.`,
            actionLabel: 'Unisci',
            accept: () => this.mergeSelectedScores()
        });
    }

    protected mergeSelectedScores(): void {
        if (!this.track.scores || this.selectedScores.length < 2) return;

        const sorted = [...this.selectedScores].sort((a, b) => a.order! - b.order!);

        const mergedMedia: ChildrenEntities[] = sorted.flatMap((s) => s.media ?? []).map((m, i) => ({ index: m.index, name: m.name, order: i + 1 }));

        const seenIndexes = new Set<number>();
        const mergedInstruments: ChildrenEntities[] = [];
        for (const s of sorted) {
            for (const inst of s.instruments ?? []) {
                if (!seenIndexes.has(inst.index)) {
                    seenIndexes.add(inst.index);
                    mergedInstruments.push({ index: inst.index, name: inst.name, order: mergedInstruments.length + 1 });
                }
            }
        }

        const merged = new SheetsMusic();
        merged.order = sorted[0].order!;
        merged.description = sorted[0].description;
        merged.media = mergedMedia;
        merged.instruments = mergedInstruments;

        const selectedOrders = new Set(sorted.map((s) => s.order));
        this.track.scores = this.track.scores.filter((s) => !selectedOrders.has(s.order));
        this.track.scores.push(merged);
        this.track.scores.sort((a, b) => a.order! - b.order!).forEach((s, i) => (s.order = i + 1));

        this.selectedScores = [];
        this.isDirty = true;
    }

    protected confirmSplitScore(score: SheetsMusic): void {
        this.confirmService.confirmReversible({
            title: 'Scorpora parte',
            consequence: `La parte verrà suddivisa in ${score.media?.length} righe separate, una per pagina.`,
            actionLabel: 'Scorpora',
            accept: () => this.splitScore(score)
        });
    }

    protected splitScore(score: SheetsMusic): void {
        if (!this.track.scores || (score.media?.length ?? 0) <= 1) return;

        const scores = this.track.scores;
        const scoreIndex = scores.findIndex((s) => s.order === score.order);
        if (scoreIndex < 0) return;

        const newScores: SheetsMusic[] = (score.media ?? []).map((m) => {
            const s = new SheetsMusic();
            s.description = score.description;
            s.media = [{ index: m.index, name: m.name, order: 1 }];
            s.instruments = structuredClone(score.instruments ?? []);
            return s;
        });

        this.track.scores = [...scores.slice(0, scoreIndex), ...newScores, ...scores.slice(scoreIndex + 1)];
        this.track.scores.forEach((s, i) => (s.order = i + 1));
        this.isDirty = true;
    }

    protected onGlobalFilter(table: Table<SheetsMusic>, event: Event): void {
        table.filterGlobal((event.target as HTMLInputElement).value, 'contains');
    }

    protected onRowReorder(): void {
        this.track.scores?.forEach((score, index) => {
            score.order = index + 1;
        });
        this.isDirty = true;
    }

    protected showMedia(media: ChildrenEntities[]) {
        this.displayGalleria = true;
        this.images = media.map((m) => this.mediaService.stream(m.index));
    }

    protected mediaStream(media: ChildrenEntities): string {
        return this.mediaService.stream(media.index);
    }

    protected editScore(score: SheetsMusic): void {
        const dynamicDialogRef: DynamicDialogRef = this.dialogService.open(EditScoreDialogComponent, {
            inputValues: {
                currentScoreOrder: score.order,
                scores: structuredClone(this.track.scores),
                instruments: this.instruments
            },
            header: 'Modifica parte',
            closable: false,
            showHeader: false,
            draggable: true,
            resizable: true,
            modal: true,
            width: '56rem',
            breakpoints: { '1199px': '75vw', '575px': '90vw' }
        });

        dynamicDialogRef.onClose.pipe(first()).subscribe((result: SheetsMusic[]) => {
            if (result) {
                this.track.scores = result;
                this.isDirty = true;
            }
        });
    }

    protected confirmDeleteScore(score: SheetsMusic): void {
        this.confirmService.confirmDestructive({
            title: 'Rimuovi parte',
            consequence: 'La parte verrà rimossa dalla traccia.',
            actionLabel: 'Rimuovi',
            accept: () => this.deleteScore(score)
        });
    }

    protected deleteScore(selectedScore: SheetsMusic): void {
        this.track.scores?.splice(
            this.track.scores.findIndex((score) => selectedScore.order === score.order),
            1
        );
        this.track.scores?.sort((a, b) => (a.order! < b.order! ? -1 : 1)).forEach((score, i) => (score.order = i + 1));
        this.isDirty = true;
    }

    private loadElement(id: number | string) {
        this.loading = true;
        this.tracksService
            .getById(Number(id))
            .pipe(first(), finalize(() => (this.loading = false)))
            .subscribe((track) => {
                this.track = track;
                this.isDirty = false;
            });
    }

    private loadUploadJobs(trackId: number): void {
        this.tracksService
            .getUploadJobs(trackId)
            .pipe(first())
            .subscribe((jobs) => (this.uploadJobs = jobs));
    }
}
