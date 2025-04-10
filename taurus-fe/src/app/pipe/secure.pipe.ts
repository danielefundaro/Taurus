import { HttpClient } from '@angular/common/http';
import { Pipe, type PipeTransform } from '@angular/core';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { lastValueFrom } from 'rxjs';

@Pipe({
    name: 'secure',
})
export class SecurePipe implements PipeTransform {

    constructor(private readonly http: HttpClient, private readonly sanitizer: DomSanitizer) { }

    async transform(url: string): Promise<SafeUrl> {
        return this.sanitizer.bypassSecurityTrustUrl(URL.createObjectURL(await lastValueFrom(this.http.get(url, { responseType: 'blob' }))));
    }
}