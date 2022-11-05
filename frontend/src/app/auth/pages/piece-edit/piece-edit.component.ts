import { AfterViewInit, Component, OnDestroy, OnInit, SecurityContext, ViewChild } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { ActivatedRoute, Router } from '@angular/router';
import { AngularEditorConfig } from '@kolkov/angular-editor';
import { TranslateService } from '@ngx-translate/core';
import { firstValueFrom, Subscription } from 'rxjs';
import { SnackBarService } from 'src/app/services';
import { DeleteDialogComponent } from '../../components/delete-dialog/delete-dialog.component';
import { ViewMediaDialogComponent } from '../../components/view-media-dialog/view-media-dialog.component';
import { Instrument, Media, MediaTypeEnum, PersonalDataSource, Piece, PieceTypeEnum, PositionEnum } from '../../models';
import { InstrumentService, MediaService, PieceService } from '../../services';

@Component({
    selector: 'piece-edit',
    templateUrl: './piece-edit.component.html',
    styleUrls: ['./piece-edit.component.scss']
})
export class PieceEditComponent implements OnInit, AfterViewInit, OnDestroy {
    public get name() { return this.formGroup.get('name'); }
    public get type() { return this.formGroup.get('type'); }
    public get author() { return this.formGroup.get('author'); }
    public get arranger() { return this.formGroup.get('arranger'); }
    public get description() { return this.formGroup.get('description'); }
    private get id() { return this.formGroup.get('id'); }

    public formGroup: FormGroup;
    public pieceTypeEnum = PieceTypeEnum;
    public dataSource!: PersonalDataSource<Media>;
    public displayedColumns = ["order", "name", "type", "page", "instrument", "actions"];
    public imageMediaType = MediaTypeEnum.Image;
    public readOnly!: boolean;
    public instruments: Instrument[] = [];
    public PositionEnumFirst = PositionEnum.First;
    public PositionEnumLast = PositionEnum.Last;
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

    @ViewChild(MatPaginator, { static: false }) private paginator!: MatPaginator;
    @ViewChild(MatSort, { static: false }) private sort!: MatSort;

    private param?: Subscription;
    private fragment?: Subscription;
    private media: Array<Media> = new Array<Media>();

    constructor(private router: Router, private route: ActivatedRoute, private pieceService: PieceService,
        private instrumentService: InstrumentService, private mediaService: MediaService,
        private translate: TranslateService, private dialog: MatDialog, private snackBar: SnackBarService) {
        this.readOnly = false;
        this.dataSource = new PersonalDataSource<Media>();
        this.formGroup = new FormGroup({
            id: new FormControl(undefined),
            name: new FormControl(undefined, [Validators.required]),
            type: new FormControl(undefined),
            author: new FormControl(undefined),
            arranger: new FormControl(undefined),
            description: new FormControl(undefined),
        });
    }

    ngOnInit(): void {
        this.fragment = this.route.fragment.subscribe(data => {
            if (this.readOnly = data === "show") {
                this.id?.disable();
                this.name?.disable();
                this.type?.disable();
                this.author?.disable();
                this.arranger?.disable();
                this.description?.disable();
                this.editorConfig.editable = !this.readOnly;
            }
        });

        this.param = this.route.params.subscribe(data => {
            const id = data['id'];

            if (id !== 'add') {
                firstValueFrom(this.pieceService.getById(id)).then(piece => {
                    this.id?.setValue(piece.id);
                    this.name?.setValue(piece.name);
                    this.type?.setValue(piece.type);
                    this.author?.setValue(piece.author);
                    this.arranger?.setValue(piece.arranger);
                    this.description?.setValue(piece.description);
                    this.media = piece.media?.map(media => new Media().clone(media)) || [];

                    if (this.media) {
                        Promise.all(this.media?.map(media => firstValueFrom(this.mediaService.stream(media.id)))).then(data => {
                            this.media = this.mediaService.bunchSanitizer(data, this.media) || [];
                        }).catch(error => {
                            this.snackBar.error(this.translate.instant("PIECES.ERROR.LOAD_MEDIA", {'status': error.error?.status || error.status, 'message': error.error?.error || error.message}), error.error?.status || error.status);
                        });
                    }

                    if (!this.dataSource.paginator || !this.dataSource.sort) {
                        this.dataSource = new PersonalDataSource<Media>(this.paginator, this.sort);
                    }

                    this.dataSource.set(this.media || []);

                    firstValueFrom(this.instrumentService.getAll()).then(data => {
                        this.instruments = data;
                        this.media?.forEach(media => media.instrument = this.instruments.find(instrument => instrument.id == media.instrument?.id));
                    }).catch(error => {
                        this.snackBar.error(this.translate.instant("PIECES.ERROR.LOAD_INSTRUMENTS", {'status': error.error?.status || error.status, 'message': error.error?.error || error.message}), error.error?.status || error.status);
                    });
                }).catch(error => {
                    this.snackBar.error(this.translate.instant("PIECES.ERROR.LOAD", {'status': error.error?.status || error.status, 'message': error.error?.error || error.message}), error.error?.status || error.status);
                });
            }
        });
    }

    ngAfterViewInit(): void {
        this.dataSource = new PersonalDataSource<Media>(this.paginator, this.sort);
        this.dataSource.set(this.media || []);
    }

    ngOnDestroy(): void {
        this.fragment?.unsubscribe();
        this.param?.unsubscribe();
    }

    public savePiece(): void {
        const piece = new Piece();

        piece.id = this.id?.value;
        piece.name = this.name?.value;
        piece.type = Piece.convertType(this.type?.value);
        piece.author = this.author?.value;
        piece.arranger = this.arranger?.value;
        piece.description = this.description?.value;
        piece.media = this.dataSource.data;

        firstValueFrom(this.pieceService.save(piece)).then(savedPiece => {
            if (savedPiece.media) {
                Promise.all(savedPiece.media?.map((media, i) => {
                    const file: File | undefined = piece.media?.at(i)?.file;

                    if (file) {
                        return firstValueFrom(this.mediaService.saveFile(media.id, file));
                    }

                    return undefined;
                }).filter(observable => observable)).then(() => {
                    this.snackBar.success(this.translate.instant("PIECES.SUCCESS.SAVE"));
                    this.router.navigate(['pieces', savedPiece?.id]);
                }).catch(error => {
                    this.snackBar.error(this.translate.instant("PIECES.ERROR.SAVE", {'status': error.error?.status || error.status, 'message': error.error?.error || error.message}), error.error?.status || error.status);
                });
            } else {
                this.snackBar.success(this.translate.instant("PIECES.SUCCESS.SAVE"));
                this.router.navigate(['pieces', savedPiece?.id]);
            }
        }).catch(error => {
            this.snackBar.error(this.translate.instant("PIECES.ERROR.SAVE", {'status': error.error?.status || error.status, 'message': error.error?.error || error.message}), error.error?.status || error.status);
        });
    }

    public onFileDrop(files: File[]): void {
        for (const file of files) {
            const media = new Media(file);
            const maxOrder = this.dataSource.data.slice().sort((a, b) => a.order && b.order ? (a.order > b.order ? -1 : 1) : 0).at(0)?.order;
    
            media.order = maxOrder ? maxOrder + 1 : 1;
            media.page = 1;
            this.dataSource.push(media);
        }
    }

    public showMedia(media: Media) {
        this.dialog.open(ViewMediaDialogComponent, {
            data: {
                title: this.translate.instant("MEDIA.DIALOG.SHOW.TITLE"),
                media: media,
            }
        });
    }

    public editMedia(media: Media) {
        this.dialog.open(ViewMediaDialogComponent, {
            data: {
                title: this.translate.instant("MEDIA.DIALOG.SHOW.TITLE"),
                media: media,
                edit: true,
            }
        });
    }

    public moveMedia(media: Media, position: PositionEnum): void {
        media.move(this.media, position);
        this.dataSource.refresh.emit();
    }

    public changeOrderMedia(media: Media, order: number): void {
        media.changeOrder(this.media, order);
        this.dataSource.refresh.emit();
    }

    public deleteMedia(index: number): void {
        firstValueFrom(this.dialog.open(DeleteDialogComponent, {
            data: {
                title: this.translate.instant("MEDIA.DIALOG.DELETE.TITLE"),
                message: this.translate.instant("MEDIA.DIALOG.DELETE.MESSAGE")
            },
            disableClose: true
        }).afterClosed()).then(result => {
            if (result) {
                this.dataSource.removeAt(index);
            }
        });
    }
}
