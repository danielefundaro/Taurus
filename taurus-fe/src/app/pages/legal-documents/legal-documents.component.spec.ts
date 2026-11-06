import { Router } from '@angular/router';
import { of } from 'rxjs';
import { LegalDocument } from '../../module';
import { LegalService } from '../../service';
import { LegalDocumentsComponent } from './legal-documents.component';

describe('LegalDocumentsComponent', () => {
    const version = (overrides: Partial<LegalDocument> = {}): LegalDocument => ({
        id: 1,
        documentType: 'TERMS',
        version: '1.3',
        title: 'Termini di utilizzo',
        action: 'ACCEPT',
        required: true,
        state: 'ACTIVE',
        contentSha256: 'a'.repeat(64),
        url: null,
        ...overrides
    });

    it('lists the versions and opens a new document in its page', () => {
        const legal = jasmine.createSpyObj<LegalService>('LegalService', ['getDocuments']);
        const router = jasmine.createSpyObj<Router>('Router', ['navigate']);
        router.navigate.and.resolveTo(true);
        legal.getDocuments.and.returnValue(of([version(), version({ id: 2, version: '1.2', state: 'RETIRED', contentSha256: null, url: 'https://example.test/termini' })]));
        const component = new LegalDocumentsComponent(legal, router);
        const state = component as unknown as Record<string, any>;

        component.ngOnInit();
        state['addDocument']();

        expect(state['documents'].length).toBe(2);
        expect(state['toComplete'](state['documents'][0])).toBeFalse();
        expect(state['toComplete'](state['documents'][1])).toBeTrue();
        expect(state['stateLabel']('RETIRED')).toBe('Ritirata');
        expect(router.navigate).toHaveBeenCalledWith(['/legal-documents/new']);
    });
});
