import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, HttpClientModule } from '@angular/common/http';

import { TranslateModule, TranslateLoader, TranslateService } from '@ngx-translate/core';
import { TranslateHttpLoader } from '@ngx-translate/http-loader';
import { LanguageService } from '../services/language.service';


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
    constructor(private translate: TranslateService, private languageSelected: LanguageService) {
        this.translate.addLangs(languageSelected.languages);
        this.translate.setDefaultLang(languageSelected.default);

        this.languageSelected.languageChanged().subscribe(data => {
            this.translate.use(data);
        });
    }
}
