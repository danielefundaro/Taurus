import { AsyncPipe, NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AutoCompleteCompleteEvent, AutoCompleteModule } from 'primeng/autocomplete';
import { ButtonModule } from 'primeng/button';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { FloatLabelModule } from 'primeng/floatlabel';
import { FluidModule } from 'primeng/fluid';
import { InputNumberInputEvent, InputNumberModule } from 'primeng/inputnumber';
import { OrderListModule } from 'primeng/orderlist';
import { PickListModule } from 'primeng/picklist';
import { Popover, PopoverModule } from 'primeng/popover';
import { first } from 'rxjs';
import { ChildrenEntities, Instruments, InstrumentsCriteria, Page, SheetsMusic } from '../../module';
import { StringFilter } from '../../module/criteria/filter';
import { SecurePipe } from '../../pipe/secure.pipe';
import { InstrumentsService, MediaService } from '../../service';
import { SelectModule } from 'primeng/select';

@Component({
    selector: 'app-edit-score-dialog',
    imports: [
        NgIf,
        AsyncPipe,
        ButtonModule,
        FloatLabelModule,
        FormsModule,
        FluidModule,
        AutoCompleteModule,
        InputNumberModule,
        SelectModule,
        OrderListModule,
        PickListModule,
        PopoverModule,
        SecurePipe,
    ],
    templateUrl: './edit-score-dialog.component.html',
    styleUrl: './edit-score-dialog.component.scss',
    providers: [
        InstrumentsService,
        MediaService,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditScoreDialogComponent {

    @Input() public currentScoreOrder: number;
    @Input() public scores: SheetsMusic[];
    protected currentScore: SheetsMusic;
    protected selectedScore?: SheetsMusic;
    protected autoFilteredInstruments: ChildrenEntities[];
    private totalAutoFilteredInstruments: number;

    constructor(private readonly dialogRef: DynamicDialogRef<EditScoreDialogComponent>,
        private readonly config: DynamicDialogConfig<any, { currentScoreOrder: number, scores: SheetsMusic[] }>,
        private readonly instrumentsService: InstrumentsService,
        private readonly mediaService: MediaService,
    ) {
        this.currentScoreOrder = this.config.inputValues?.currentScoreOrder ?? -1;
        this.scores = this.config.inputValues?.scores ?? [];

        this.currentScore = this.scores.find(score => score.order === this.currentScoreOrder) ?? {};
        this.autoFilteredInstruments = [];
        this.totalAutoFilteredInstruments = 0;
    }

    protected onReorderScores(event: InputNumberInputEvent): void {
        const previeusOrder = Number.parseInt(event.formattedValue);

        if (!event.value) {
            this.currentScore.order = previeusOrder;
        }

        let selectedScores = this.scores.filter(score => score.order === this.currentScore.order);

        if (selectedScores.length > 1) {
            for (let score of selectedScores) {
                if (score !== this.currentScore) {
                    score.order = previeusOrder;
                }
            }

            this.scores.sort((a, b) => a.order! < b.order! ? -1 : 1);
        }
    }

    protected filterInstruments(event: AutoCompleteCompleteEvent) {
        const instrumentsCriteria = new InstrumentsCriteria();
        instrumentsCriteria.name = new StringFilter();
        instrumentsCriteria.name.contains = event.query;
        instrumentsCriteria.page = 0;
        instrumentsCriteria.sort = ['name.keyword,asc'];

        this.instrumentsService.getAll(instrumentsCriteria).pipe(first()).subscribe({
            next: (pageInstruments: Page<Instruments>) => {
                this.totalAutoFilteredInstruments = pageInstruments.totalElements;
                this.autoFilteredInstruments = pageInstruments.content.map(instrument => {
                    const childrenEntities = new ChildrenEntities();
                    childrenEntities.name = instrument.name;
                    childrenEntities.index = instrument.id;

                    return childrenEntities;
                });
            }
        });
    }

    protected onReorderInstruments(): void {
        this.currentScore.instruments?.forEach((instrument, i) => instrument.order = i + 1);
    }

    protected onReorderMedia(score: SheetsMusic): void {
        score?.media?.forEach((media, i) => media.order = i + 1);
    }

    protected toggleDataTable(op: Popover, event: any) {
        op.toggle(event);
    }

    protected mediaStream(media: ChildrenEntities): string {
        return this.mediaService.stream(media.index);
    }

    protected cancel(): void {
        this.dialogRef.close();
    }

    protected save(): void {
        this.scores.forEach(score => {
            if (score.order === this.currentScore.order) {
                if (this.currentScore.description) {
                    score.description = this.currentScore.description;
                }

                if (this.currentScore.instruments) {
                    score.instruments = this.currentScore.instruments;
                }

                if (this.currentScore.media) {
                    score.media = this.currentScore.media;
                }
            }
        });

        this.dialogRef.close(this.scores);
    }
}
