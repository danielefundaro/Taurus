import { of } from 'rxjs';
import { InventoryLoanSheetSummary } from '../../module';
import { UserInventoryService } from '../../service';
import { OwnLoanSheetsComponent } from './own-loan-sheets.component';

describe('OwnLoanSheetsComponent', () => {
    const sheet = (id: number, overrides: Partial<InventoryLoanSheetSummary> = {}): InventoryLoanSheetSummary => ({
        id,
        sheetNumber: `2026/00${id}`,
        status: 'ISSUED',
        issuedOn: '2026-10-20',
        userIndex: 1,
        memberName: 'Mario',
        memberLastName: 'Rossi',
        holderRole: 'MEMBER',
        templateLabel: 'Predefinito',
        personalDataPurged: false,
        closable: false,
        entityVersion: 1,
        signatureMode: 'PAPER',
        ...overrides
    });

    function create(sheets: InventoryLoanSheetSummary[]): OwnLoanSheetsComponent {
        const service = jasmine.createSpyObj<UserInventoryService>('UserInventoryService', ['getLoanSheets', 'downloadLoanSheet']);
        service.getLoanSheets.and.returnValue(of(sheets));
        const component = new OwnLoanSheetsComponent(service);
        component.ngOnInit();
        return component;
    }

    it('points to the signed documents only when a sheet signed electronically still has its copy', () => {
        expect(create([sheet(1)])['hasElectronic']).toBeFalse();
        expect(create([sheet(1), sheet(2, { signatureMode: 'ELECTRONIC' })])['hasElectronic']).toBeTrue();
        expect(create([sheet(1, { signatureMode: 'ELECTRONIC', personalDataPurged: true })])['hasElectronic']).toBeFalse();
    });
});
