import { Component } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ImportsModule } from '../../imports';
import { SignatureAct } from '../../module';

export interface SignaturePaperDialogData {
    act: SignatureAct;
}

/**
 * Dichiarazione che un atto è stato firmato su carta, con la nota obbligatoria: restituisce la nota, che l'amministratore
 * conferma poi con il proprio codice.
 */
@Component({
    selector: 'app-signature-paper-dialog',
    standalone: true,
    imports: [ImportsModule],
    templateUrl: './signature-paper-dialog.component.html'
})
export class SignaturePaperDialogComponent {
    protected readonly data: SignaturePaperDialogData;
    protected note = '';

    constructor(
        protected readonly dialogRef: DynamicDialogRef<SignaturePaperDialogComponent>,
        config: DynamicDialogConfig<SignaturePaperDialogData>
    ) {
        this.data = config.data!;
    }
}
