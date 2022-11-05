import { CommonFields } from "./commonFields.model";
import { Media } from "./media.model";

export enum PieceTypeEnum {
    Classic = "CLASSIC",
    Concert = "CONCERT",
    PopMusic = "POP_MUSIC",
    MilitaryMarches = "MILITARY_MARCHES",
    SynphonicMarches = "SYMPHONIC_MARCHES",
    FuneralMarches = "FUNERAL_MARCHES",
    CarnivalMarches = "CARNIVAL_MARCHES",
    ReligiousMarches = "RELIGIOUS_MARCHES"
}

export class Piece extends CommonFields {
    name!: string;
    type?: PieceTypeEnum;
    author?: string;
    arranger?: string;
    media?: Array<Media>;

    public static convertType(value?: string): PieceTypeEnum | undefined {
        let type: PieceTypeEnum | undefined = undefined;

        if (value) {
            switch (value) {
                case PieceTypeEnum.Classic: type = PieceTypeEnum.Classic; break;
                case PieceTypeEnum.Concert: type = PieceTypeEnum.Concert; break;
                case PieceTypeEnum.PopMusic: type = PieceTypeEnum.PopMusic; break;
                case PieceTypeEnum.MilitaryMarches: type = PieceTypeEnum.MilitaryMarches; break;
                case PieceTypeEnum.SynphonicMarches: type = PieceTypeEnum.SynphonicMarches; break;
                case PieceTypeEnum.FuneralMarches: type = PieceTypeEnum.FuneralMarches; break;
                case PieceTypeEnum.CarnivalMarches: type = PieceTypeEnum.CarnivalMarches; break;
                case PieceTypeEnum.ReligiousMarches: type = PieceTypeEnum.ReligiousMarches; break;
            }
        }

        return type;
    }
}