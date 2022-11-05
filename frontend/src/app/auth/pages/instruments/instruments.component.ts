import { AfterViewInit, Component, ElementRef, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { catchError, debounceTime, firstValueFrom, fromEvent, map, merge, of, startWith, switchMap } from 'rxjs';
import { SnackBarService } from 'src/app/services';
import { DeleteDialogComponent } from '../../components/delete-dialog/delete-dialog.component';
import { Instrument } from '../../models';
import { InstrumentService } from '../../services';

@Component({
    selector: 'instruments',
    templateUrl: './instruments.component.html',
    styleUrls: ['./instruments.component.scss']
})
export class InstrumentsComponent implements AfterViewInit {
    public dataSource!: MatTableDataSource<Instrument>;
    public displayedColumns = ["name", "description", "actions"];

    @ViewChild(MatPaginator, { static: false }) private paginator!: MatPaginator;
    @ViewChild(MatSort, { static: false }) private sort!: MatSort;
    @ViewChild("filter", { static: false }) private filter!: ElementRef;

    constructor(private router: Router, private instrumentService: InstrumentService, private dialog: MatDialog,
        private translate: TranslateService, private snackBar: SnackBarService) {
        this.dataSource = new MatTableDataSource<Instrument>();
    }

    ngAfterViewInit(): void {
        merge(this.paginator.page, this.sort.sortChange, fromEvent(this.filter.nativeElement, 'keyup')).pipe(
            startWith({}),
            debounceTime(150),
            switchMap(() => {
                return this.instrumentService.searches(this.filter.nativeElement.value, this.paginator.pageIndex, this.paginator.pageSize, this.sort.active, this.sort.direction)
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
        });
    }

    public addInstrument = (): void => {
        this.router.navigate(['instruments', 'add']);
    }

    public editInstrument = (id: number): void => {
        this.router.navigate(['instruments', id]);
    }

    public deleteInstrument = (id: number): void => {
        firstValueFrom(this.dialog.open(DeleteDialogComponent, {
            data: {
                title: this.translate.instant("INSTRUMENTS.DIALOG.DELETE.TITLE"),
                message: this.translate.instant("INSTRUMENTS.DIALOG.DELETE.MESSAGE")
            },
            disableClose: true
        }).afterClosed()).then(result => {
            if (result) {
                firstValueFrom(this.instrumentService.delete(id)).then(data => {
                    this.snackBar.success(this.translate.instant("INSTRUMENTS.SUCCESS.DELETE"));
                    this.paginator.page.emit();
                }).catch(error => {
                    this.snackBar.error(this.translate.instant("INSTRUMENTS.ERROR.DELETE", {'status': error.error?.status || error.status, 'message': error.error?.error || error.message}), error.error?.status || error.status);
                });
            }
        });
    }
}
