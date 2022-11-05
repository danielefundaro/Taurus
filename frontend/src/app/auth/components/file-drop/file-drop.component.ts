import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NgxFileDropEntry } from 'ngx-file-drop';

@Component({
    selector: 'file-drop',
    templateUrl: './file-drop.component.html',
    styleUrls: ['./file-drop.component.scss']
})
export class FileDropComponent {

    @Input() public disabled!: boolean;
    @Input() public multiple: boolean = true;
    @Output() private onFileDrop: EventEmitter<File[]> = new EventEmitter<File[]>();

    public dropped(files: NgxFileDropEntry[]): void {
        const finalFiles: File[] = [];

        for (const droppedFile of files) {
            if (droppedFile.fileEntry.isFile) {
                (droppedFile.fileEntry as FileSystemFileEntry).file((file: File) => {
                    finalFiles.push(file);
                });
            }
        }

        this.onFileDrop.emit(finalFiles);
    }

    public handleClick(event: any): void {
        this.onFileDrop.emit(event.target.files);
    }
}
