import { Injectable } from "@angular/core";
import { MessageService } from "primeng/api";

@Injectable({
    providedIn: 'root'
})
export class ToastService {
    constructor(private readonly service: MessageService) { }

    public success(title: string, message: string) {
        this.service.add({ severity: "success", summary: title, detail: message, life: 2000 });
    }

    public info(title: string, message: string) {
        this.service.add({ severity: "info", summary: title, detail: message, life: 2000 });
    }

    public warn(title: string, message: string) {
        this.service.add({ severity: "warn", summary: title, detail: message, life: 3000 });
    }

    public error(title: string, message: string) {
        this.service.add({ severity: "error", summary: title, detail: message, life: 5000 });
    }
}