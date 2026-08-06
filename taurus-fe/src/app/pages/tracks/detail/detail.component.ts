import { HttpClient, HttpHeaders } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ConfirmationService } from 'primeng/api';
import { AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Table } from 'primeng/table';
import { delay, finalize, first, firstValueFrom } from 'rxjs';
import { TypeHandlerComponent } from "../../../components/type-handler/type-handler.component";
import { RoleEnums, StateLabel, StateLabelsMap } from '../../../constants';
import { EditScoreDialogComponent } from '../../../dialogs/edit-score-dialog/edit-score-dialog.component';
import { PdfManipulatorDialogComponent } from '../../../dialogs/pdf-manipulator-dialog/pdf-manipulator-dialog.component';
import { HasUnsavedChanges } from '../../../guard';
import { ImportsModule } from '../../../imports';
import { ChildrenEntities, Instruments, InstrumentsCriteria, SheetsMusic, Tracks } from '../../../module';
import { PdfAnnotations } from '../../../module/pdf-annotations.module';
import { InstrumentsService, KeycloakService, MediaService, PrinterService, ToastService, TracksService } from '../../../service';

@Component({
    selector: 'app-track-detail',
    imports: [
        ImportsModule,
        TypeHandlerComponent,
    ],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [
        TracksService,
        InstrumentsService,
        KeycloakService,
        DialogService,
        ConfirmationService,
    ],
    changeDetection: ChangeDetectionStrategy.Default,
})
export class DetailComponent implements OnInit, HasUnsavedChanges {
    protected track: Tracks = new Tracks();
    protected cols: string[];
    protected selectedScores: SheetsMusic[];
    protected images: string[];
    protected displayGalleria: boolean;
    protected autoFilteredStatesLabels: StateLabel[];
    protected RolesEnum: typeof RoleEnums = RoleEnums;
    protected readonly previewTooltip = 'Aggiungi almeno una parte per abilitare l\'anteprima';
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

    isDirty = false;
    isSaving = false;
    protected selectedFile: File | null = null;
    protected annotations: PdfAnnotations | null = null;
    protected uploading = false;

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
        private readonly confirmationService: ConfirmationService,
        private readonly dialogService: DialogService,
        private readonly http: HttpClient,
    ) {
        this.cols = ["Ordine", "Media", "Strumenti"];
        this.selectedScores = [];
        this.images = [];
        this.displayGalleria = false;
        this.instruments = [];
        this.autoFilteredStatesLabels = StateLabelsMap;
    }

    ngOnInit() {
        this.routeService.params.pipe(first()).subscribe(params => {
            this.loadElement(params['id']);
        });

        let page = 0;
        const instrumentsCriteria: InstrumentsCriteria = { page: page, sort: ['name.keyword,asc'] };

        this.instrumentsService.getAll().pipe(first()).subscribe(async result => {
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
        this.confirmationService.confirm({
            header: 'Conferma eliminazione',
            message: 'Eliminare definitivamente questa traccia?',
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Elimina',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () => {
                this.tracksService.delete(this.track.id).pipe(first()).subscribe({
                    next: () => {
                        this.isDirty = false;
                        this.toastService.success('Successo', 'Traccia eliminata');
                        this.router.navigate(['/tracks']);
                    },
                });
            },
        });
    }

    protected save(): void {
        this.isSaving = true;
        this.tracksService.update(this.track.id, this.track).pipe(delay(1000), first(), finalize(() => this.isSaving = false)).subscribe({
            next: (track: Tracks) => {
                this.isDirty = false;
                this.toastService.success("Successo", "Traccia aggiornata con successo");
                this.loadElement(track.id);
            }
        });
    }

    protected preview(): void {
        this.printerService.preview(this.track);
    }

    protected filterStates(event: AutoCompleteCompleteEvent) {
        this.autoFilteredStatesLabels = StateLabelsMap.filter(state => state.name.toLowerCase().includes(event.query.toLowerCase()) ? state : null).filter(state => state !== null) as StateLabel[];
    }

    protected trackStream(): string {
        return this.tracksService.stream(this.track.id);
    }

    protected httpHeaders(): HttpHeaders {
        return new HttpHeaders({ 'Authorization': `Bearer ${this.keycloakService.token}` });
    }

    protected onTypeChange(types: string[]): void {
        this.track!.type = types;
        this.isDirty = true;
    }

    protected onUploadSuccess(): void {
        this.toastService.success('Successo', 'File caricato con successo');
    }

    protected onUploadError(): void {
        this.toastService.error('Errore', 'Caricamento file fallito');
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
                height: 'calc(90vh - 54px)',
            },
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

        const headers = new HttpHeaders({ 'Authorization': `Bearer ${this.keycloakService.token}` });
        this.http.post(this.tracksService.stream(this.track.id), formData, { headers }).subscribe({
            next: () => {
                this.uploading = false;
                this.selectedFile = null;
                this.annotations = null;
                this.onUploadSuccess();
            },
            error: () => {
                this.uploading = false;
                this.onUploadError();
            },
        });
    }

    protected get hasAnnotations(): boolean {
        return !!(this.annotations && (
            this.annotations.excludedPages.length > 0 ||
            this.annotations.cropRegions.length > 0
        ));
    }

    protected addNew(): void {
        this.track.scores ??= [];

        const score = new SheetsMusic();
        const max = Math.max(...this.track.scores.map(score => score.order!), 0);
        score.order = max + 1;
        score.media = [];
        score.instruments = [];

        this.track.scores.push(score);
        this.isDirty = true;
    }

    protected confirmDeleteSelectedScores(): void {
        this.confirmationService.confirm({
            header: 'Conferma eliminazione',
            message: 'Rimuovere le parti selezionate dalla traccia?',
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Rimuovi',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () => this.deleteSelectedScores(),
        });
    }

    protected deleteSelectedScores(): void {
        for (let selectedScore of this.selectedScores) {
            this.deleteScore(selectedScore);
        }
        this.selectedScores = [];
    }

    protected confirmMergeSelectedScores(): void {
        this.confirmationService.confirm({
            header: 'Conferma unione',
            message: `Unire le ${this.selectedScores.length} parti selezionate in una sola? I media verranno concatenati nell'ordine delle righe.`,
            icon: 'pi pi-link',
            acceptLabel: 'Unisci',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'primary' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () => this.mergeSelectedScores(),
        });
    }

    protected mergeSelectedScores(): void {
        if (!this.track.scores || this.selectedScores.length < 2) return;

        const sorted = [...this.selectedScores].sort((a, b) => a.order! - b.order!);

        const mergedMedia: ChildrenEntities[] = sorted
            .flatMap(s => s.media ?? [])
            .map((m, i) => ({ index: m.index, name: m.name, order: i + 1 }));

        const seenIndexes = new Set<string>();
        const mergedInstruments: ChildrenEntities[] = [];
        for (const s of sorted) {
            for (const inst of (s.instruments ?? [])) {
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

        const selectedOrders = new Set(sorted.map(s => s.order));
        this.track.scores = this.track.scores.filter(s => !selectedOrders.has(s.order));
        this.track.scores.push(merged);
        this.track.scores.sort((a, b) => a.order! - b.order!).forEach((s, i) => s.order = i + 1);

        this.selectedScores = [];
        this.isDirty = true;
    }

    protected confirmSplitScore(score: SheetsMusic): void {
        this.confirmationService.confirm({
            header: 'Conferma scorporo',
            message: `Scorporare questa parte in ${score.media?.length} righe separate (una per pagina)?`,
            icon: 'pi pi-table',
            acceptLabel: 'Scorporo',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'primary' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () => this.splitScore(score),
        });
    }

    protected splitScore(score: SheetsMusic): void {
        if (!this.track.scores || (score.media?.length ?? 0) <= 1) return;

        const scoreIndex = this.track.scores.findIndex(s => s.order === score.order);
        const newScores: SheetsMusic[] = (score.media ?? []).map(m => {
            const s = new SheetsMusic();
            s.description = score.description;
            s.media = [{ index: m.index, name: m.name, order: 1 }];
            s.instruments = structuredClone(score.instruments ?? []);
            return s;
        });

        this.track.scores.splice(scoreIndex, 1, ...newScores);
        this.track.scores.forEach((s, i) => s.order = i + 1);
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
        this.images = media.map(m => this.mediaService.stream(m.index));
    }

    protected mediaStream(media: ChildrenEntities): string {
        return this.mediaService.stream(media.index);
    }

    protected editScore(score: SheetsMusic): void {
        const dynamicDialogRef: DynamicDialogRef = this.dialogService.open(EditScoreDialogComponent, {
            inputValues: {
                currentScoreOrder: score.order,
                scores: structuredClone(this.track.scores),
                instruments: this.instruments,
            },
            header: "Modifica parte",
            closable: true,
            draggable: true,
            resizable: true,
            modal: true,
            width: '50vw',
            breakpoints: { '1199px': '75vw', '575px': '90vw' },
        });

        dynamicDialogRef.onClose.pipe(first()).subscribe((result: SheetsMusic[]) => {
            if (result) {
                this.track.scores = result;
                this.isDirty = true;
            }
        });
    }

    protected confirmDeleteScore(score: SheetsMusic): void {
        this.confirmationService.confirm({
            header: 'Conferma eliminazione',
            message: 'Rimuovere questa parte dalla traccia?',
            icon: 'pi pi-exclamation-triangle',
            acceptLabel: 'Rimuovi',
            rejectLabel: 'Annulla',
            acceptButtonProps: { severity: 'danger' },
            rejectButtonProps: { severity: 'secondary' },
            accept: () => this.deleteScore(score),
        });
    }

    protected deleteScore(selectedScore: SheetsMusic): void {
        this.track.scores?.splice(this.track.scores.findIndex(score => selectedScore.order === score.order), 1);
        this.track.scores?.sort((a, b) => a.order! < b.order! ? -1 : 1).forEach((score, i) => score.order = i + 1);
        this.isDirty = true;
    }

    private loadElement(id: string) {
        this.tracksService.getById(id).pipe(first()).subscribe(track => {
            this.track = track;
            this.isDirty = false;
        });
    }
}
