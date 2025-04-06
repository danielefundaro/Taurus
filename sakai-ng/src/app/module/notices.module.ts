import { CommonFields } from ".";

export class Notices extends CommonFields {
    name!: string;
    message?: string;
    readDate?: Date;
}