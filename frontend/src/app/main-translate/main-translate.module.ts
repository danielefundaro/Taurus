import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpClientModule } from '@angular/common/http';

import { TranslateModule, TranslateLoader, TranslateService } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';
import { SettingsService } from '../services/settings.service';


export function createTranslateLoader(http: HttpClient) {
    return new TranslateHttpLoader(http, '/assets/i18n/', '.json');
}

@NgModule({
    imports: [
        CommonModule,
        HttpClientModule,
        TranslateModule.forRoot({
            loader: {
                provide: TranslateLoader,
                useFactory: (createTranslateLoader),
                deps: [HttpClient]
            },
            isolate: true
        })
    ],
    exports: [TranslateModule],
    providers: [TranslateService]
})

export class MainTranslateModule {
    constructor(private translate: TranslateService, private settingsService: SettingsService) {
        this.translate.addLangs(settingsService.languages);
        this.translate.setDefaultLang(settingsService.default);

        this.settingsService.languageChanged().subscribe(data => {
            this.translate.use(data);
        });
    }
}
