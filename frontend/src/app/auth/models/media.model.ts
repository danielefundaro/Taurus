import { SafeUrl } from "@angular/platform-browser";
import { Instrument } from "./instrument.model";
import { OrderedItem } from "./orderedItem.model";

export enum MediaTypeEnum {
    Image = "IMAGE",
    Pdf = "PDF",
    Audio = "AUDIO",
    Video = "VIDEO",
    Other = "OTHER"
}

export class Media extends OrderedItem {
    public name!: string;
    public type?: MediaTypeEnum;
    public contentType?: string;
    public page?: number;
    public instrument?: Instrument;
    public file?: File;
    public preview?: string | SafeUrl | null;

    private reader = new FileReader();

    constructor(file?: File) {
        super();

        this.reader.onload = (e) => this.onload(e);

        if (file) {
            this.fileAttributes(file);
        }
    }

    public setFile(file: File): void {
        this.fileAttributes(file);
    }

    public clone(media: Media): Media {
        const m: Media = new Media();

        m.id = media.id;
        m.name = media.name;
        m.type = media.type;
        m.contentType = media.contentType;
        m.page = media.page;
        m.order = media.order;
        m.instrument = media.instrument;
        m.file = media.file;
        m.preview = media.preview;
        m.description = media.description;

        return m;
    }

    private onload(e: ProgressEvent<FileReader>) {
        this.preview = e.target?.result?.toString();
        const dataType = this.preview?.toString().split(";")[0];

        if (dataType?.match("pdf")) {
            this.type = MediaTypeEnum.Pdf;
        } else if (dataType?.match("image")) {
            this.type = MediaTypeEnum.Image;
        } else if (dataType?.match("audio")) {
            this.type = MediaTypeEnum.Audio;
        } else if (dataType?.match("video")) {
            this.type = MediaTypeEnum.Video;
        } else {
            this.type = MediaTypeEnum.Other;
        }
    }

    private fileAttributes(file: File) {
        this.file = file;
        this.name = file.name;
        this.contentType = file.type;
        this.reader.readAsDataURL(file);
    }
}