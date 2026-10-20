import { Injectable } from '@angular/core';
import { DialogService } from 'primeng/dynamicdialog';
import { first, map, Observable, of, switchMap } from 'rxjs';
import { SignatureAct, SignatureCodeRequest, SignatureConfirmation } from '../../module';
import { SignatureService, ToastService } from '../../service';
import { SignatureCodeDialogComponent, SignatureCodeDialogData, SignatureCodeDialogResult } from '../signature-code-dialog/signature-code-dialog.component';
import { SignatureActDialogComponent, SignatureActDialogData, SignatureActDialogResult } from './signature-act-dialog.component';

/**
 * Firma di un atto, passo dopo passo: la finestra dell'atto raccoglie i dati e la lettura del documento, la finestra del
 * codice registra la firma, o la registra direttamente il caricamento CieSign, e l'atto si riapre finché restano firme
 * dell'utente, per esempio l'approvazione delle
 * clausole dopo l'accettazione del foglio. Va fornito dal componente insieme a {@link DialogService}.
 */
@Injectable()
export class SignatureActFlow {
    constructor(
        private readonly dialogService: DialogService,
        private readonly signatureService: SignatureService,
        private readonly toastService: ToastService
    ) {}

    /** Firma l'atto; emette l'atto aggiornato, o `undefined` se la finestra è stata chiusa senza cambiamenti. */
    sign(act: SignatureAct): Observable<SignatureAct | undefined> {
        return this.openAct(act).pipe(
            switchMap((result) => {
                if (!result) return of(undefined);
                if (!result.request && !result.outcome) return of(result.act === act ? undefined : result.act);
                const confirmed = result.request ? this.confirm(result.request, result.act) : of(true);
                return confirmed.pipe(
                    switchMap((confirmed) => {
                        if (!confirmed) return of(result.act);
                        return this.signatureService.getAct(act.id).pipe(
                            first(),
                            switchMap((updated) => {
                                if (updated.pendingPurposes.length || updated.dataRequired) return this.sign(updated).pipe(map((next) => next ?? updated));
                                this.toastService.success(
                                    updated.status === 'SIGNED' ? 'Atto firmato' : 'Firma registrata',
                                    updated.status === 'SIGNED' ? 'Tutte le parti hanno firmato: i documenti firmati e l’attestazione sono disponibili.' : 'La tua firma è registrata; l’atto attende le altre firme.'
                                );
                                return of(updated);
                            })
                        );
                    })
                );
            })
        );
    }

    /** Conferma con il codice una firma già preparata, per esempio più variazioni insieme. */
    confirm(request: SignatureCodeRequest, act?: SignatureAct): Observable<boolean> {
        return this.confirmWith(request, act).pipe(map((confirmation) => !!confirmation));
    }

    /** Come {@link confirm}, ma emette la conferma registrata con l'esito degli atti, o `undefined` se manca. */
    confirmWith(request: SignatureCodeRequest, act?: SignatureAct): Observable<SignatureConfirmation | undefined> {
        const data: SignatureCodeDialogData = {
            heading: 'Conferma con il codice',
            subtitle: act ? `Foglio n. ${act.sheetNumber ?? ''} · ${act.operation}` : request.items.length > 1 ? `${request.items.length} atti` : undefined,
            request
        };
        return this.dialogService
            .open(SignatureCodeDialogComponent, {
                header: data.heading,
                showHeader: false,
                modal: true,
                width: '34rem',
                breakpoints: { '767px': 'calc(100vw - 1rem)' },
                data
            })
            .onClose.pipe(
                first(),
                map((result?: SignatureCodeDialogResult) => result?.confirmation)
            );
    }

    /** Apre la finestra dell'atto: dati dell'Affidatario oppure lettura del documento da firmare. */
    openAct(act: SignatureAct): Observable<SignatureActDialogResult | undefined> {
        const data: SignatureActDialogData = { act };
        return this.dialogService
            .open(SignatureActDialogComponent, {
                header: 'Firma dell’atto',
                showHeader: false,
                modal: true,
                width: act.dataRequired ? '44rem' : '36rem',
                breakpoints: { '767px': 'calc(100vw - 1rem)' },
                data
            })
            .onClose.pipe(first());
    }
}
