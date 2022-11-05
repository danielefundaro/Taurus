import { CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { firstValueFrom, Subscription } from 'rxjs';
import { SnackBarService } from 'src/app/services';
import { Media, MediaTypeEnum } from '../../models';
import { PrinterService } from '../../services';

@Component({
    selector: 'preview',
    templateUrl: './preview.component.html',
    styleUrls: ['./preview.component.scss']
})
export class PreviewComponent implements OnInit, OnDestroy {
    public mediaList!: Media[];
    public mediaImage = MediaTypeEnum.Image;
    private printerSubscription!: Subscription;

    constructor(public printerService: PrinterService, private translate: TranslateService,
        private snackBar: SnackBarService) {
    }

    ngOnInit(): void {
        this.mediaList = this.printerService.media;
        this.printerSubscription = this.printerService.connect().subscribe(data => {
            this.mediaList = data;
        });

        if (this.mediaList.filter(media => media.type === this.mediaImage).length == 0) {
            firstValueFrom(this.translate.get("PREVIEW.ERROR.LOAD", { 'status': 404, 'message': 'Media not found' })).then(message => {
                this.snackBar.error(message, 404);
            });
        }
    }

    ngOnDestroy(): void {
        this.printerService.clear();
        this.printerSubscription.unsubscribe();
    }

    public print(): void {
        window.print();
    }

    public drop(event: CdkDragDrop<Media[]>): void {
        moveItemInArray(this.mediaList, event.previousIndex, event.currentIndex);
    }
}
