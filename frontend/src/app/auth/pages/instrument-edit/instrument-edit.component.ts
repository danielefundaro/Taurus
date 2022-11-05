import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AngularEditorConfig } from '@kolkov/angular-editor';
import { TranslateService } from '@ngx-translate/core';
import { firstValueFrom, Subscription } from 'rxjs';
import { SnackBarService } from 'src/app/services';
import { Instrument } from '../../models';
import { InstrumentService } from '../../services';

@Component({
    selector: 'instrument-edit',
    templateUrl: './instrument-edit.component.html',
    styleUrls: ['./instrument-edit.component.scss']
})
export class InstrumentEditComponent implements OnInit, OnDestroy {
    public get id() { return this.formGroup.get('id'); }
    public get name() { return this.formGroup.get('name'); }
    public get description() { return this.formGroup.get('description'); }

    public formGroup: FormGroup;
    public editorConfig: AngularEditorConfig = {
        editable: true,
        spellcheck: true,
        height: 'auto',
        minHeight: '100',
        maxHeight: 'auto',
        width: 'auto',
        minWidth: '100',
        translate: 'yes',
        enableToolbar: true,
        showToolbar: true,
        defaultParagraphSeparator: '',
        defaultFontName: '',
        defaultFontSize: '',
        fonts: [
            { class: 'arial', name: 'Arial' },
            { class: 'times-new-roman', name: 'Times New Roman' },
            { class: 'calibri', name: 'Calibri' },
            { class: 'comic-sans-ms', name: 'Comic Sans MS' }
        ],
        uploadUrl: 'v1/image',
        uploadWithCredentials: false,
        sanitize: true,
        toolbarPosition: 'top',
    };

    private param?: Subscription;

    constructor(private router: Router, private route: ActivatedRoute, private instrumentService: InstrumentService,
        private translate: TranslateService, private snackBar: SnackBarService) {
        this.formGroup = new FormGroup({
            id: new FormControl(undefined),
            name: new FormControl(undefined, [Validators.required]),
            description: new FormControl(undefined),
        });
    }

    ngOnInit(): void {
        this.param = this.route.params.subscribe(data => {
            const id = data['id'];

            if (id !== 'add') {
                firstValueFrom(this.instrumentService.getById(id)).then(instrument => {
                    this.id?.setValue(instrument.id);
                    this.name?.setValue(instrument.name);
                    this.description?.setValue(instrument.description);
                }).catch(error => {
                    this.snackBar.error(this.translate.instant("INSTRUMENTS.ERROR.LOAD", {'status': error.error?.status || error.status, 'message': error.error?.error || error.message}), error.error?.status || error.status);
                });
            }
        });
    }

    ngOnDestroy(): void {
        this.param?.unsubscribe();
    }

    public saveInstrument(): void {
        const instrument = new Instrument();
        instrument.id = this.id?.value;
        instrument.name = this.name?.value;
        instrument.description = this.description?.value;

        firstValueFrom(this.instrumentService.save(instrument)).then(data => {
            this.snackBar.success(this.translate.instant("INSTRUMENTS.SUCCESS.SAVE"));
            this.router.navigate(['instruments', data?.id]);
        }).catch(error => {
            this.snackBar.error(this.translate.instant("INSTRUMENTS.ERROR.SAVE", {'status': error.error?.status || error.status, 'message': error.error?.error || error.message}), error.error?.status || error.status);
        });
    }
}
