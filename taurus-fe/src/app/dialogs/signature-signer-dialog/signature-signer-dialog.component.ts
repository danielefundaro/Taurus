import { Component } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ImportsModule } from '../../imports';
import { SignatureAct, SignatureAssociationSignerOption } from '../../module';

export interface SignatureSignerDialogData {
    act: SignatureAct;
    options: SignatureAssociationSignerOption[];
}

/**
 * Designazione di un altro firmatario per l'Associazione, finché nessuno ha firmato l'atto: restituisce l'utente scelto.
 */
@Component({
    selector: 'app-signature-signer-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './signature-signer-dialog.component.html'
})
export class SignatureSignerDialogComponent {
    protected readonly data: SignatureSignerDialogData;
    protected readonly current?: string;
    protected userId?: string;

    constructor(
        protected readonly dialogRef: DynamicDialogRef<SignatureSignerDialogComponent>,
        config: DynamicDialogConfig<SignatureSignerDialogData>
    ) {
        this.data = config.data!;
        this.current = this.data.act.signers.find((signer) => signer.role === 'ASSOCIATION')?.userId;
    }
}
