import { AfterViewInit, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormGroup, FormControl, Validators } from '@angular/forms';
import { DateAdapter, MAT_DATE_LOCALE } from '@angular/material/core';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { Router, ActivatedRoute } from '@angular/router';
import { AngularEditorConfig } from '@kolkov/angular-editor';
import { TranslateService } from '@ngx-translate/core';
import { Subscription, firstValueFrom, Observable, map, startWith, switchMap } from 'rxjs';
import { SnackBarService } from 'src/app/services';
import { DeleteDialogComponent } from '../../components/delete-dialog/delete-dialog.component';
import { PersonalDataSource, Collection, Album, Piece, PositionEnum } from '../../models';
import { AlbumService, PieceService } from '../../services';

@Component({
    selector: 'album-edit',
    templateUrl: './album-edit.component.html',
    styleUrls: ['./album-edit.component.scss'],
    providers: [{ provide: MAT_DATE_LOCALE, useValue: 'en-GB' }],
})
export class AlbumEditComponent implements OnInit, AfterViewInit, OnDestroy {
    public get name() { return this.formGroup.get('name'); }
    public get date() { return this.formGroup.get('date'); }
    public get description() { return this.formGroup.get('description'); }
    public get pieceFilter() { return this.formGroup.get('pieceFilter'); }
    private get id() { return this.formGroup.get('id'); }

    public formGroup: FormGroup;
    public dataSource!: PersonalDataSource<Collection>;
    public displayedColumns = ['order', 'name', 'type', 'author', 'arranger', 'description', 'actions'];
    public readOnly!: boolean;
    public filteredPiecesOptions?: Observable<Piece[]>;
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
    private collections: Array<Collection> = new Array<Collection>();

    constructor(private router: Router, private route: ActivatedRoute, private albumService: AlbumService,
        private pieceService: PieceService, private translate: TranslateService, private dialog: MatDialog,
        private dateAdapter: DateAdapter<Date>, private snackBar: SnackBarService) {
        this.dateAdapter.setLocale(translate.currentLang);
        this.readOnly = false;
        this.dataSource = new PersonalDataSource<Collection>();
        this.formGroup = new FormGroup({
            id: new FormControl(undefined),
            name: new FormControl(undefined, [Validators.required]),
            date: new FormControl(undefined),
            pieceFilter: new FormControl(undefined),
            description: new FormControl(undefined),
        });
    }

    ngOnInit(): void {
        this.filteredPiecesOptions = this.pieceFilter?.valueChanges.pipe(
            startWith(this.pieceFilter.value),
            switchMap(value => firstValueFrom(this.pieceService.searchesExcludeIds(this.collections.map(collection => collection.piece.id), value, 0, 20, 'name', 'asc'))),
            map(value => value.content)
        );

        this.fragment = this.route.fragment.subscribe(data => {
            if (this.readOnly = data === "show") {
                this.id?.disable();
                this.name?.disable();
                this.date?.disable();
                this.description?.disable();
                this.editorConfig.editable = !this.readOnly;
            }
        });

        this.param = this.route.params.subscribe(data => {
            const id = data['id'];

            if (id !== 'add') {
                firstValueFrom(this.albumService.getById(id)).then(album => {
                    this.id?.setValue(album.id);
                    this.name?.setValue(album.name);
                    this.date?.setValue(album.date);
                    this.description?.setValue(album.description);
                    this.collections = album.collections?.map(collection => new Collection().clone(collection)) || [];

                    if (!this.dataSource.paginator || !this.dataSource.sort) {
                        this.dataSource = new PersonalDataSource<Collection>(this.paginator, this.sort);
                    }

                    this.dataSource.set(this.collections);
                }).catch(error => {
                    this.snackBar.error(this.translate.instant("ALBUMS.ERROR.LOAD", {'status': error.error?.status || error.status, 'message': error.error?.error || error.message}), error.error?.status || error.status);
                });
            }
        });
    }

    ngAfterViewInit(): void {
        this.dataSource = new PersonalDataSource<Collection>(this.paginator, this.sort);
        this.dataSource.set(this.collections);
    }

    ngOnDestroy(): void {
        this.fragment?.unsubscribe();
        this.param?.unsubscribe();
    }

    public saveAlbum(): void {
        const album = new Album();

        album.id = this.id?.value;
        album.name = this.name?.value;
        album.date = this.date?.value;
        album.description = this.description?.value;
        album.collections = this.dataSource.data;

        firstValueFrom(this.albumService.save(album)).then(data => {
            this.snackBar.success(this.translate.instant("ALBUMS.SUCCESS.SAVE"));
            this.router.navigate(['albums', data?.id]);
        }).catch(error => {
            this.snackBar.error(this.translate.instant("ALBUMS.ERROR.SAVE", {'status': error.error?.status || error.status, 'message': error.error?.error || error.message}), error.error?.status || error.status);
        });
    }

    public pieceName(piece: Piece): string {
        const array: Array<string | undefined> = [];

        array.push(piece.name);
        array.push(this.translate.instant(`PIECES.ENUMS.${piece.type}`));
        array.push(piece.author);
        array.push(piece.arranger);
        array.push(piece.description);

        return array.filter(a => a).join(" - ");
    }

    public addCollection(piece: Piece) {
        const collection = new Collection();
        const maxOrder = this.dataSource.data.slice().sort((a, b) => a.order && b.order ? (a.order > b.order ? -1 : 1) : 0).at(0)?.order;

        collection.order = maxOrder ? maxOrder + 1 : 1;
        collection.piece = piece;

        this.dataSource.push(collection);
    }

    public showPiece = (id: number): void => {
        this.router.navigate(['pieces', id], { fragment: 'show' });
    }

    public moveCollection(collection: Collection, position: PositionEnum): void {
        collection.move(this.collections, position);
        this.dataSource.refresh.emit();
    }

    public changeOrderCollection(collection: Collection, order: number): void {
        collection.changeOrder(this.collections, order);
        this.dataSource.refresh.emit();
    }

    public deleteCollection(index: number): void {
        firstValueFrom(this.dialog.open(DeleteDialogComponent, {
            data: {
                title: this.translate.instant("ALBUMS.DIALOG.DELETE.TITLE"),
                message: this.translate.instant("ALBUMS.DIALOG.DELETE.MESSAGE")
            },
            disableClose: true
        }).afterClosed()).then(result => {
            if (result) {
                this.dataSource.removeAt(index);
            }
        });
    }
}
