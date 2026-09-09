import { ChangeDetectionStrategy, Component, ElementRef, EventEmitter, Input, OnChanges, Output, QueryList, SimpleChanges, ViewChildren } from '@angular/core';
import { AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { ImportsModule } from '../../../../imports';
import { ChildrenEntities, Instruments, SheetsMusic } from '../../../../module';
import { ConfirmService } from '../../../../service';
import { MediaThumbnailComponent } from '../media-thumbnail/media-thumbnail.component';
import { cloneAndNormalizeScores, mergeScores, moveMedia, reorderMedia, reorderScore, splitScoreAfter, splitScoreByPage } from './score-workspace.operations';

interface ScoreListItem {
    score: SheetsMusic;
    index: number;
}

@Component({
    selector: 'app-score-workspace',
    standalone: true,
    imports: [ImportsModule, MediaThumbnailComponent],
    templateUrl: './score-workspace.component.html',
    styleUrl: './score-workspace.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ScoreWorkspaceComponent implements OnChanges {
    @Input() scores: SheetsMusic[] = [];
    @Input() instruments: Instruments[] = [];
    @Input() readOnly = false;
    @Input() saving = false;
    @Input() trackDirty = false;
    @Output() scoresChange = new EventEmitter<SheetsMusic[]>();
    @Output() dirtyChange = new EventEmitter<boolean>();
    @Output() imageEdit = new EventEmitter<{ scoreId: number; media: ChildrenEntities; pageNumber: number; scoreTitle: string }>();
    @ViewChildren('pageCard') private pageCards?: QueryList<ElementRef<HTMLElement>>;

    protected draftScores: SheetsMusic[] = [];
    protected selectedScoreIndex = 0;
    protected selectedMediaIds = new Set<number>();
    protected selectedPartKeys = new Set<string>();
    protected filterQuery = '';
    protected filteredInstrumentOptions: ChildrenEntities[] = [];
    protected moveTargetIndex: number | null = null;
    protected selectingParts = false;
    protected mobileDetail = false;
    protected liveMessage = '';
    protected activePageIndex = 0;

    private undoStack: SheetsMusic[][] = [];
    private baselineScores: SheetsMusic[] = [];
    private lastEmitted?: SheetsMusic[];
    private inlineEditActive = false;
    private draggedScoreIndex?: number;
    private draggedPage?: { scoreIndex: number; pageIndex: number; mediaId: number };

    constructor(private readonly confirmService: ConfirmService) {}

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['scores'] && this.scores !== this.lastEmitted) {
            this.draftScores = cloneAndNormalizeScores(this.scores);
            this.baselineScores = cloneAndNormalizeScores(this.scores);
            this.undoStack = [];
            this.selectedScoreIndex = Math.min(this.selectedScoreIndex, Math.max(0, this.draftScores.length - 1));
            this.activePageIndex = 0;
            this.clearSelections();
            this.dirtyChange.emit(false);
        }
    }

    protected get selectedScore(): SheetsMusic | undefined {
        return this.draftScores[this.selectedScoreIndex];
    }

    protected get filteredScores(): ScoreListItem[] {
        const query = this.filterQuery.trim().toLocaleLowerCase('it');
        return this.draftScores
            .map((score, index) => ({ score, index }))
            .filter(({ score }) => {
                if (!query) return true;
                return [score.description, ...(score.instruments ?? []).map((instrument) => instrument.name)].some((value) => value?.toLocaleLowerCase('it').includes(query));
            });
    }

    protected get canUndo(): boolean {
        return this.undoStack.length > 0 && !this.saving;
    }

    protected get selectedPageIndex(): number {
        if (this.selectedMediaIds.size !== 1 || !this.selectedScore) return -1;
        return this.selectedScore.media?.findIndex((media) => this.selectedMediaIds.has(media.index)) ?? -1;
    }

    protected get selectedMedia(): ChildrenEntities | undefined {
        const index = this.selectedPageIndex;
        return index < 0 ? undefined : this.selectedScore?.media?.[index];
    }

    protected get moveTargets(): { label: string; value: number }[] {
        return this.draftScores.map((score, index) => ({ label: `${index + 1}. ${this.scoreTitle(score)}`, value: index })).filter((option) => option.value !== this.selectedScoreIndex);
    }

    protected get allFilteredPartsSelected(): boolean {
        return this.filteredScores.length > 0 && this.filteredScores.every(({ score }) => this.selectedPartKeys.has(this.scoreKey(score)));
    }

    protected get allPagesSelected(): boolean {
        const media = this.selectedScore?.media ?? [];
        return media.length > 0 && media.every((item) => this.selectedMediaIds.has(item.index));
    }

    protected scoreKey = (score: SheetsMusic): string => {
        if (score.id !== undefined) return `id:${score.id}`;
        return `draft:${score.order ?? this.draftScores.indexOf(score)}`;
    };

    protected scoreTitle(score: SheetsMusic): string {
        return score.description?.trim() || score.instruments?.[0]?.name || 'Parte senza nome';
    }

    protected pageLabel(score: SheetsMusic, pageIndex: number, media: ChildrenEntities): string {
        return `${this.scoreTitle(score)}, pagina ${pageIndex + 1}${media.name ? `, ${media.name}` : ''}`;
    }

    protected selectScore(index: number): void {
        this.selectedScoreIndex = index;
        this.activePageIndex = 0;
        this.selectedMediaIds = new Set<number>();
        this.moveTargetIndex = null;
        this.mobileDetail = true;
    }

    protected clearSearch(): void {
        this.filterQuery = '';
    }

    protected addScore(): void {
        const next = cloneAndNormalizeScores([...this.draftScores, { description: '', media: [], instruments: [], needsReview: false }]);
        this.commit(next, 'Nuova parte creata', next.length - 1);
        this.mobileDetail = true;
    }

    protected beginInlineEdit(): void {
        if (this.inlineEditActive) return;
        this.undoStack.push(cloneAndNormalizeScores(this.draftScores));
        this.inlineEditActive = true;
    }

    protected endInlineEdit(): void {
        this.inlineEditActive = false;
    }

    protected updateInline(message: string): void {
        this.draftScores = cloneAndNormalizeScores(this.draftScores);
        this.emitDraft(message);
    }

    protected onDescriptionChange(description: string): void {
        if (!this.selectedScore) return;
        this.selectedScore.description = description;
        this.updateInline('Descrizione della parte modificata');
    }

    protected onInstrumentsChange(instruments: ChildrenEntities[]): void {
        if (!this.selectedScore) return;
        this.selectedScore.instruments = instruments ?? [];
        this.updateInline('Strumenti della parte modificati');
    }

    protected filterInstruments(event: AutoCompleteCompleteEvent): void {
        const query = event.query.toLocaleLowerCase('it');
        const selected = new Set((this.selectedScore?.instruments ?? []).map((instrument) => instrument.index));
        this.filteredInstrumentOptions = this.instruments
            .filter((instrument) => !selected.has(instrument.id) && (!query || instrument.name?.toLocaleLowerCase('it').includes(query)))
            .map((instrument) => ({ index: instrument.id, name: instrument.name }));
    }

    protected toggleMedia(mediaId: number): void {
        const selected = new Set(this.selectedMediaIds);
        selected.has(mediaId) ? selected.delete(mediaId) : selected.add(mediaId);
        this.selectedMediaIds = selected;
    }

    protected togglePart(score: SheetsMusic): void {
        const selected = new Set(this.selectedPartKeys);
        const key = this.scoreKey(score);
        selected.has(key) ? selected.delete(key) : selected.add(key);
        this.selectedPartKeys = selected;
    }

    protected toggleAllParts(selected: boolean): void {
        const next = new Set(this.selectedPartKeys);
        this.filteredScores.forEach(({ score }) => (selected ? next.add(this.scoreKey(score)) : next.delete(this.scoreKey(score))));
        this.selectedPartKeys = next;
    }

    protected clearPartSelection(): void {
        this.selectedPartKeys = new Set<string>();
    }

    protected toggleAllPages(selected: boolean): void {
        this.selectedMediaIds = selected ? new Set((this.selectedScore?.media ?? []).map((media) => media.index)) : new Set<number>();
    }

    protected clearPageSelection(): void {
        this.selectedMediaIds = new Set<number>();
        this.moveTargetIndex = null;
    }

    protected togglePartSelectionMode(): void {
        this.selectingParts = !this.selectingParts;
        this.selectedPartKeys = new Set<string>();
    }

    protected confirmMerge(): void {
        if (this.selectedPartKeys.size < 2) return;
        this.confirmService.confirmReversible({
            title: 'Unisci parti',
            consequence: 'Le pagine saranno concatenate nell’ordine delle parti selezionate. Gli strumenti duplicati verranno rimossi.',
            actionLabel: 'Unisci',
            accept: () => {
                const first = this.draftScores.findIndex((score) => this.selectedPartKeys.has(this.scoreKey(score)));
                this.commit(mergeScores(this.draftScores, this.selectedPartKeys, this.scoreKey), `${this.selectedPartKeys.size} parti unite`, Math.max(0, first));
                this.selectingParts = false;
                this.selectedPartKeys = new Set<string>();
            }
        });
    }

    protected confirmDeleteScore(): void {
        const score = this.selectedScore;
        if (!score) return;
        this.confirmService.confirmReversible({
            title: 'Rimuovi parte',
            consequence: `La parte “${this.scoreTitle(score)}” e le sue ${score.media?.length ?? 0} pagine verranno rimosse dalla traccia. Potrai annullare l’operazione prima del salvataggio.`,
            actionLabel: 'Rimuovi',
            accept: () => {
                const next = this.draftScores.filter((_, index) => index !== this.selectedScoreIndex);
                this.commit(next, 'Parte eliminata', Math.min(this.selectedScoreIndex, Math.max(0, next.length - 1)));
            }
        });
    }

    protected duplicateScore(): void {
        if (!this.selectedScore) return;
        const copy = structuredClone(this.selectedScore);
        copy.id = undefined;
        copy.description = copy.description?.trim() ? `${copy.description} (copia)` : '';
        const next = [...this.draftScores];
        next.splice(this.selectedScoreIndex + 1, 0, copy);
        this.commit(next, 'Parte duplicata', this.selectedScoreIndex + 1);
    }

    protected confirmDeletePages(): void {
        if (!this.selectedMediaIds.size || !this.selectedScore) return;
        const count = this.selectedMediaIds.size;
        this.confirmService.confirmReversible({
            title: count === 1 ? 'Rimuovi pagina' : 'Rimuovi pagine',
            consequence: `${count} ${count === 1 ? 'pagina verrà rimossa' : 'pagine verranno rimosse'} dalla parte. Potrai annullare l’operazione prima del salvataggio.`,
            actionLabel: 'Rimuovi',
            accept: () => {
                const next = cloneAndNormalizeScores(this.draftScores);
                next[this.selectedScoreIndex].media = next[this.selectedScoreIndex].media?.filter((media) => !this.selectedMediaIds.has(media.index));
                this.commit(next, `${count} ${count === 1 ? 'pagina eliminata' : 'pagine eliminate'}`);
                this.selectedMediaIds = new Set<number>();
            }
        });
    }

    protected moveSelectedPages(): void {
        if (this.moveTargetIndex === null || !this.selectedMediaIds.size || this.moveTargetIndex === this.selectedScoreIndex) return;
        const count = this.selectedMediaIds.size;
        this.commit(moveMedia(this.draftScores, this.selectedMediaIds, this.moveTargetIndex), `${count} ${count === 1 ? 'pagina spostata' : 'pagine spostate'}`, this.moveTargetIndex);
        this.moveTargetIndex = null;
    }

    protected createScoreFromPages(): void {
        if (!this.selectedMediaIds.size || !this.selectedScore) return;
        const next = cloneAndNormalizeScores(this.draftScores);
        const source = next[this.selectedScoreIndex];
        const media = (source.media ?? []).filter((item) => this.selectedMediaIds.has(item.index));
        source.media = (source.media ?? []).filter((item) => !this.selectedMediaIds.has(item.index));
        next.splice(this.selectedScoreIndex + 1, 0, { description: '', instruments: [], media });
        this.commit(next, 'Nuova parte creata dalle pagine selezionate', this.selectedScoreIndex + 1);
    }

    protected confirmSplitAfter(): void {
        const pageIndex = this.selectedPageIndex;
        if (pageIndex < 0 || !this.selectedScore || pageIndex === (this.selectedScore.media?.length ?? 0) - 1) {
            this.announce('Seleziona una sola pagina che non sia l’ultima della parte.');
            return;
        }
        const createdPages = (this.selectedScore.media?.length ?? 0) - pageIndex - 1;
        this.confirmService.confirmReversible({
            title: 'Dividi parte',
            consequence: `Verrà creata una nuova parte con le ${createdPages} pagine successive. Gli strumenti saranno mantenuti in entrambe.`,
            actionLabel: 'Dividi',
            accept: () => {
                this.commit(splitScoreAfter(this.draftScores, this.selectedScoreIndex, pageIndex), 'Parte divisa');
            }
        });
    }

    protected confirmSplitByPage(): void {
        const count = this.selectedScore?.media?.length ?? 0;
        if (count < 2) return;
        this.confirmService.confirmReversible({
            title: 'Una parte per pagina',
            consequence: `La parte verrà sostituita da ${count} parti, una per pagina. Gli strumenti saranno mantenuti in tutte.`,
            actionLabel: 'Dividi',
            accept: () => this.commit(splitScoreByPage(this.draftScores, this.selectedScoreIndex), `${count} parti create`)
        });
    }

    protected undo(): void {
        const previous = this.undoStack.pop();
        if (!previous) return;
        this.draftScores = cloneAndNormalizeScores(previous);
        this.selectedScoreIndex = Math.min(this.selectedScoreIndex, Math.max(0, this.draftScores.length - 1));
        this.clearSelections();
        this.emitDraft('Ultima operazione annullata');
    }

    protected moveScore(index: number, direction: -1 | 1): void {
        const target = index + direction;
        if (!this.draftScores[target] || this.filterQuery.trim()) return;
        this.commit(reorderScore(this.draftScores, index, target), 'Parti riordinate', target);
    }

    protected movePage(pageIndex: number, direction: -1 | 1): void {
        const target = pageIndex + direction;
        if (!this.selectedScore?.media?.[target]) return;
        this.commit(reorderMedia(this.draftScores, this.selectedScoreIndex, pageIndex, target), 'Pagine riordinate');
    }

    protected onScoreDragStart(index: number): void {
        if (!this.filterQuery.trim()) this.draggedScoreIndex = index;
    }

    protected onScoreDrop(targetIndex: number, event: DragEvent): void {
        event.preventDefault();
        if (this.draggedScoreIndex === undefined || this.filterQuery.trim()) return;
        this.commit(reorderScore(this.draftScores, this.draggedScoreIndex, targetIndex), 'Parti riordinate', targetIndex);
        this.draggedScoreIndex = undefined;
    }

    protected onScoreDragEnd(): void {
        this.draggedScoreIndex = undefined;
    }

    protected onPageDragStart(scoreIndex: number, pageIndex: number, mediaId: number): void {
        this.draggedPage = { scoreIndex, pageIndex, mediaId };
        if (!this.selectedMediaIds.has(mediaId)) this.selectedMediaIds = new Set([mediaId]);
    }

    protected onPageDragEnd(): void {
        this.draggedPage = undefined;
    }

    protected onPageDrop(targetScoreIndex: number, targetPageIndex: number | undefined, event: DragEvent): void {
        event.preventDefault();
        if (!this.draggedPage) return;
        const selected = this.selectedMediaIds.has(this.draggedPage.mediaId) ? this.selectedMediaIds : new Set([this.draggedPage.mediaId]);
        const next = moveMedia(this.draftScores, selected, targetScoreIndex, targetPageIndex);
        this.commit(next, targetScoreIndex === this.draggedPage.scoreIndex ? 'Pagine riordinate' : 'Pagine spostate', targetScoreIndex);
        this.draggedPage = undefined;
    }

    protected onPartKeydown(index: number, event: KeyboardEvent): void {
        if (event.key === 'Enter') this.selectScore(index);
        if (event.ctrlKey && (event.key === 'ArrowUp' || event.key === 'ArrowDown')) {
            event.preventDefault();
            this.moveScore(index, event.key === 'ArrowUp' ? -1 : 1);
        }
    }

    protected setActivePage(pageIndex: number): void {
        this.activePageIndex = pageIndex;
    }

    protected editImage(score: SheetsMusic, media: ChildrenEntities, pageIndex: number): void {
        if (this.readOnly || this.trackDirty || this.saving || score.id === undefined) return;
        this.imageEdit.emit({ scoreId: score.id, media, pageNumber: pageIndex + 1, scoreTitle: this.scoreTitle(score) });
    }

    protected navigateToPage(pageIndex: number): void {
        const pageCount = this.selectedScore?.media?.length ?? 0;
        if (!pageCount) return;

        this.activePageIndex = Math.max(0, Math.min(pageIndex, pageCount - 1));
        queueMicrotask(() => {
            const pageCard = this.pageCards?.get(this.activePageIndex)?.nativeElement;
            pageCard?.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' });
            pageCard?.focus({ preventScroll: true });
        });
        this.announce(`Pagina ${this.activePageIndex + 1} di ${pageCount}`);
    }

    protected onPageKeydown(pageIndex: number, mediaId: number, thumbnail: MediaThumbnailComponent, event: KeyboardEvent): void {
        if (event.target !== event.currentTarget) return;
        this.setActivePage(pageIndex);
        if (event.key === 'Enter') {
            event.preventDefault();
            thumbnail.openPreview();
            return;
        }
        if (event.key === ' ') {
            event.preventDefault();
            this.toggleMedia(mediaId);
        }
        if (event.ctrlKey && (event.key === 'ArrowLeft' || event.key === 'ArrowRight')) {
            event.preventDefault();
            this.movePage(pageIndex, event.key === 'ArrowLeft' ? -1 : 1);
            return;
        }
        if (!event.ctrlKey && ['ArrowLeft', 'ArrowUp', 'ArrowRight', 'ArrowDown'].includes(event.key)) {
            event.preventDefault();
            this.navigateToPage(pageIndex + (event.key === 'ArrowLeft' || event.key === 'ArrowUp' ? -1 : 1));
        }
    }

    private commit(next: SheetsMusic[], message: string, selectedIndex = this.selectedScoreIndex): void {
        this.undoStack.push(cloneAndNormalizeScores(this.draftScores));
        this.draftScores = cloneAndNormalizeScores(next);
        this.selectedScoreIndex = Math.min(selectedIndex, Math.max(0, this.draftScores.length - 1));
        this.activePageIndex = Math.min(this.activePageIndex, Math.max(0, (this.selectedScore?.media?.length ?? 0) - 1));
        this.emitDraft(message);
    }

    private emitDraft(message: string): void {
        this.lastEmitted = this.draftScores;
        this.scoresChange.emit(this.draftScores);
        this.dirtyChange.emit(JSON.stringify(this.draftScores) !== JSON.stringify(this.baselineScores));
        this.announce(message);
    }

    private clearSelections(): void {
        this.selectedMediaIds = new Set<number>();
        this.selectedPartKeys = new Set<string>();
        this.moveTargetIndex = null;
    }

    private announce(message: string): void {
        this.liveMessage = '';
        queueMicrotask(() => (this.liveMessage = message));
    }
}
