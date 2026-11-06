import { publicPage } from './public-pages';

describe('publicPage', () => {
    it('opens without login the published legal documents and the signature page of a parent', () => {
        for (const path of ['/legal/terms', '/legal/terms/1.3', '/legal/privacy', '/legal/privacy/2026-1', '/firma/' + 'A'.repeat(43)]) {
            expect(publicPage(path)).withContext(path).toBeTrue();
        }
    });

    it('keeps the login on every other page, the acceptance of the documents included', () => {
        for (const path of ['/', '/legal/accept', '/legal-documents', '/legal-documents/7', '/legal/termsX', '/legal/privacy-old', '/legal', '/firmare', '/inventory']) {
            expect(publicPage(path)).withContext(path).toBeFalse();
        }
    });
});
