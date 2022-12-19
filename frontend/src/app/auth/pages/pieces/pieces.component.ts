import { AfterViewInit, Component, ElementRef, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { catchError, debounceTime, firstValueFrom, fromEvent, map, merge, of, startWith, switchMap } from 'rxjs';
import { Piece } from 'src/app/auth/models';
import { MediaService, PieceService, PrinterService } from 'src/app/auth/services';
import { SettingsService, SnackBarService } from 'src/app/services';
import { DeleteDialogComponent } from '../../components/delete-dialog/delete-dialog.component';

@Component({
    selector: 'pieces',
    templateUrl: './pieces.component.html',
    styleUrls: ['./pieces.component.scss'],
})
export class PiecesComponent implements AfterViewInit {
    public dataSource!: MatTableDataSource<Piece>;
    public displayedColumns: string[] = ['name', 'type', 'author', 'arranger', 'description', 'actions'];

    @ViewChild(MatPaginator, { static: false }) private paginator!: MatPaginator;
    @ViewChild(MatSort, { static: false }) private sort!: MatSort;
    @ViewChild("filter", { static: false }) private filter!: ElementRef;

    constructor(private router: Router, private pieceService: PieceService, private dialog: MatDialog,
        private translate: TranslateService, private snackBar: SnackBarService, private mediaService: MediaService,
        private printerService: PrinterService, private settingsService: SettingsService) {
        this.dataSource = new MatTableDataSource<Piece>(new Array<Piece>());
        this.settingsService.isLoading = true;
    }

    ngAfterViewInit(): void {
        merge(this.paginator.page, this.sort.sortChange, fromEvent(this.filter.nativeElement, 'keyup')).pipe(
            startWith({}),
            debounceTime(150),
            switchMap(() => {
                return this.pieceService.searches(this.filter.nativeElement.value, this.paginator.pageIndex, this.paginator.pageSize, this.sort.active, this.sort.direction)
                    .pipe(catchError(() => of(null)));
            }),
            map(data => {
                if (data === null) {
                    return [];
                }

                // Only refresh the result length if there is new data. In case of rate
                // limit errors, we do not want to reset the paginator to zero, as that
                // would prevent users from re-triggering requests.
                this.paginator.length = data.totalElements;
                return data.content;
            })
        ).subscribe(data => {
            this.dataSource.data = data;
            this.settingsService.isLoading = false;
        });
        const paginatorIntl = this.paginator._intl;
        this.translate.get('SHARED.PAGINATOR.FIRST').subscribe(data => { paginatorIntl.firstPageLabel = data; });
        this.translate.get('SHARED.PAGINATOR.NEXT').subscribe(data => { paginatorIntl.nextPageLabel = data; });
        this.translate.get('SHARED.PAGINATOR.PREVIOUS').subscribe(data => { paginatorIntl.previousPageLabel = data; });
        this.translate.get('SHARED.PAGINATOR.LAST').subscribe(data => { paginatorIntl.lastPageLabel = data; });
        this.translate.get('SHARED.PAGINATOR.ITEMS_PER_PAGE').subscribe(data => { paginatorIntl.itemsPerPageLabel = data; });
    }

    public addPiece = (): void => {
        this.router.navigate(['pieces', 'add']);
    }

    public showPiece = (id: number): void => {
        this.router.navigate(['pieces', id], { fragment: 'show' });
    }

    public editPiece = (id: number): void => {
        this.router.navigate(['pieces', id]);
    }

    public printPiece = (piece: Piece): void => {
        if (piece.media) {
            this.settingsService.isLoading = true;

            Promise.all(piece.media.map(media => firstValueFrom(this.mediaService.stream(media.id)))).then(streams => {
                piece.media = this.mediaService.bunchSanitizer(streams, piece.media);

                if (piece.media) {
                    this.printerService.push(...piece.media.sort((a, b) => a.order <= b.order ? -1 : 1));
                    this.printerService.preview();
                }
            }).then(() => this.settingsService.isLoading = false);
        }
    }

    public deletePiece = (id: number): void => {
        firstValueFrom(this.dialog.open(DeleteDialogComponent, {
            data: {
                title: this.translate.instant("PIECES.DIALOG.DELETE.TITLE"),
                message: this.translate.instant("PIECES.DIALOG.DELETE.MESSAGE")
            },
            disableClose: true
        }).afterClosed()).then(result => {
            if (result) {
                this.settingsService.isLoading = true;

                firstValueFrom(this.pieceService.delete(id)).then(() => {
                    this.snackBar.success(this.translate.instant("PIECES.SUCCESS.DELETE"));
                    this.paginator.page.emit();
                }).catch(error => {
                    this.snackBar.error(this.translate.instant("PIECES.ERROR.DELETE", { 'status': error.error?.status || error.status, 'message': error.error?.error || error.message }), error.error?.status || error.status);
                }).then(() => this.settingsService.isLoading = false);
            }
        });
    }
}