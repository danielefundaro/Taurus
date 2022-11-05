import { Component, Inject, } from '@angular/core';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { Media, MediaTypeEnum } from '../../models';

@Component({
    selector: 'view-media-dialog',
    templateUrl: './view-media-dialog.component.html',
    styleUrls: ['./view-media-dialog.component.scss']
})
export class ViewMediaDialogComponent {
    public mediaImage = MediaTypeEnum.Image;
    public mediaAudio = MediaTypeEnum.Audio;
    public mediaVideo = MediaTypeEnum.Video;
    public mediaPdf = MediaTypeEnum.Pdf;

    constructor(@Inject(MAT_DIALOG_DATA) public data: { title: string, media: Media, edit: boolean }) {
        if (!data.edit) {
            data.edit = false;
        }
    }

    public onFileDrop(files: File[]) {
        this.data.media.setFile(files[0]);
    }
}
