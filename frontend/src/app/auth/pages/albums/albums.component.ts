import { AfterViewInit, Component, ElementRef, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { merge, fromEvent, startWith, debounceTime, switchMap, catchError, of, map, firstValueFrom } from 'rxjs';
import { SettingsService, SnackBarService } from 'src/app/services';
import { DeleteDialogComponent } from '../../components/delete-dialog/delete-dialog.component';
import { Album } from '../../models';
import { AlbumService } from '../../services';

@Component({
    selector: 'albums',
    templateUrl: './albums.component.html',
    styleUrls: ['./albums.component.scss']
})
export class AlbumsComponent implements AfterViewInit {
    public dataSource!: MatTableDataSource<Album>;
    public displayedColumns: string[] = ['name', 'date', 'description', 'actions'];

    @ViewChild(MatPaginator) paginator!: MatPaginator;
    @ViewChild(MatSort) sort!: MatSort;
    @ViewChild("filter", { static: false }) private filter!: ElementRef;

    constructor(private translate: TranslateService, private router: Router, private albumService: AlbumService,
        private dialog: MatDialog, private snackBar: SnackBarService, private settingsService: SettingsService) {
        this.dataSource = new MatTableDataSource<Album>();
        this.settingsService.isLoading = true;
    }

    ngAfterViewInit(): void {
        this.dataSource.paginator = this.paginator;
        this.dataSource.sort = this.sort;

        merge(this.paginator.page, this.sort.sortChange, fromEvent(this.filter.nativeElement, 'keyup')).pipe(
            startWith({}),
            debounceTime(150),
            switchMap(() => {
                return this.albumService.searches(this.filter.nativeElement.value, this.paginator.pageIndex, this.paginator.pageSize, this.sort.active, this.sort.direction)
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
    }

    public showAlbum = (id: number): void => {
        this.router.navigate(['albums', id], { fragment: 'show' });
    }

    public addAlbum = (): void => {
        this.router.navigate(['albums', 'add']);
    }

    public editAlbum = (id: number): void => {
        this.router.navigate(['albums', id]);
    }

    public deleteAlbum = (id: number) => {
        firstValueFrom(this.dialog.open(DeleteDialogComponent, {
            data: {
                title: this.translate.instant("ALBUMS.DIALOG.DELETE.TITLE"),
                message: this.translate.instant("ALBUMS.DIALOG.DELETE.MESSAGE")
            },
            disableClose: true
        }).afterClosed()).then(result => {
            if (result) {
                this.settingsService.isLoading = true;

                firstValueFrom(this.albumService.delete(id)).then(() => {
                    this.snackBar.success(this.translate.instant("ALBUMS.SUCCESS.DELETE"));
                    this.paginator.page.emit();
                }).catch(error => {
                    this.snackBar.error(this.translate.instant("ALBUMS.ERROR.DELETE", {'status': error.error?.status || error.status, 'message': error.error?.error || error.message}), error.error?.status || error.status);
                }).then(() => this.settingsService.isLoading = false);
            }
        });
    }
}
