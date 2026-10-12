import { Component, OnInit } from '@angular/core';
import { first } from 'rxjs';
import { inventoryLoanSheetStatusLabel, inventoryLoanSheetStatusSeverity } from '../../constants';
import { ImportsModule } from '../../imports';
import { InventoryLoanSheetSummary } from '../../module';
import { UserInventoryService } from '../../service';

/**
 * Loan sheets of the signed-in member, with the download of the PDF while the sheet keeps its personal data.
 */
@Component({
    selector: 'app-own-loan-sheets',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './own-loan-sheets.component.html',
    styleUrl: './own-loan-sheets.component.scss'
})
export class OwnLoanSheetsComponent implements OnInit {
    protected sheets: InventoryLoanSheetSummary[] = [];
    protected readonly statusLabel = inventoryLoanSheetStatusLabel;
    protected readonly statusSeverity = inventoryLoanSheetStatusSeverity;

    constructor(private readonly userInventoryService: UserInventoryService) {}

    ngOnInit(): void {
        this.userInventoryService
            .getLoanSheets()
            .pipe(first())
            .subscribe((sheets) => (this.sheets = sheets));
    }

    protected download(sheet: InventoryLoanSheetSummary): void {
        this.userInventoryService
            .downloadLoanSheet(sheet.id)
            .pipe(first())
            .subscribe((blob) => {
                const url = URL.createObjectURL(blob);
                const anchor = document.createElement('a');
                anchor.href = url;
                anchor.download = `foglio-affidamento-${sheet.sheetNumber.replace('/', '-')}.pdf`;
                anchor.click();
                URL.revokeObjectURL(url);
            });
    }
}
