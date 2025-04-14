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
import { SelectModule } from 'primeng/select';
import { ChildrenEntities, Instruments, SheetsMusic } from '../../module';
import { SecurePipe } from '../../pipe/secure.pipe';
import { MediaService } from '../../service';

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
        MediaService,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditScoreDialogComponent {

    @Input() public currentScoreOrder: number;
    @Input() public scores: SheetsMusic[];
    @Input() public instruments: ChildrenEntities[];
    protected currentScore: SheetsMusic;
    protected selectedScore?: SheetsMusic;
    protected autoFilteredInstruments: ChildrenEntities[];

    constructor(private readonly dialogRef: DynamicDialogRef<EditScoreDialogComponent>,
        private readonly config: DynamicDialogConfig<any, { currentScoreOrder: number, scores: SheetsMusic[], instruments: Instruments[] }>,
        private readonly mediaService: MediaService,
    ) {
        this.currentScoreOrder = this.config.inputValues?.currentScoreOrder ?? -1;
        this.scores = this.config.inputValues?.scores ?? [];
        this.instruments = this.config.inputValues?.instruments.map(instrument => {
            const childrenEntities = new ChildrenEntities();
            childrenEntities.name = instrument.name;
            childrenEntities.index = instrument.id;

            return childrenEntities;
        }) ?? [];

        this.currentScore = this.scores.find(score => score.order === this.currentScoreOrder) ?? {};
        this.autoFilteredInstruments = this.instruments;
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
        this.autoFilteredInstruments = this.instruments.filter(instrument => instrument.name?.toLowerCase()?.includes(event.query.toLowerCase()));
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
