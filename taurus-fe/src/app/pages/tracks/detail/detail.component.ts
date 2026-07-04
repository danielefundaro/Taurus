import { HttpHeaders } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { ConfirmationService } from 'primeng/api';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Popover } from 'primeng/popover';
import { Table } from 'primeng/table';
import { delay, finalize, first, firstValueFrom } from 'rxjs';
import { TypeHandlerComponent } from "../../../components/type-handler/type-handler.component";
import { RoleEnums, StateEnums } from '../../../constants';
import { EditScoreDialogComponent } from '../../../dialogs/edit-score-dialog/edit-score-dialog.component';
import { ImportsModule } from '../../../imports';
import { ChildrenEntities, Instruments, InstrumentsCriteria, SheetsMusic, Tracks } from '../../../module';
import { EnumConverterPipe } from '../../../pipe';
import { InstrumentsService, KeycloakService, MediaService, PrinterService, ToastService, TracksService } from '../../../service';
import { HasUnsavedChanges } from '../../../guard/unsaved-changes.guard';

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
    protected autoFilteredStates: Array<StateEnums>;
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

    private instruments: Instruments[];
    private draggedMedia?: ChildrenEntities = undefined;
    private startDraggedScore?: SheetsMusic = undefined;
    private readonly states: Array<StateEnums>;

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
        private readonly enumConverterPipe: EnumConverterPipe<StateEnums>,
    ) {
        this.cols = ["Ordine", "Media", "Strumenti"];
        this.selectedScores = [];
        this.images = [];
        this.displayGalleria = false;
        this.instruments = [];

        this.states = this.enumConverterPipe.transform(StateEnums as unknown as StateEnums);
        this.autoFilteredStates = this.states;
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
        this.autoFilteredStates = this.states.filter(state => state?.toLowerCase()?.includes(event.query.toLowerCase()));
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

    protected toggleDataTable(op: Popover, event: any) {
        op.toggle(event);
    }

    protected dragStart(startDraggedScore: SheetsMusic, media: ChildrenEntities) {
        this.startDraggedScore = startDraggedScore;
        this.draggedMedia = media;
    }

    protected drop(endDraggedScore: SheetsMusic) {
        if (this.draggedMedia && this.startDraggedScore != endDraggedScore) {
            endDraggedScore.media?.push(this.draggedMedia);
            this.startDraggedScore?.media?.splice(this.startDraggedScore.media.findIndex(m => m.index === this.draggedMedia!.index), 1);
            this.startDraggedScore = undefined;
            this.draggedMedia = undefined;
            this.isDirty = true;
        }
    }

    protected dragEnd() {
        this.startDraggedScore = undefined;
        this.draggedMedia = undefined;
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
