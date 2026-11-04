import { GuidedTourText, TenantFeature } from '../module';
import { composeGuidedTour, GUIDED_TOUR_MAX_STEPS, GUIDED_TOUR_STEPS } from './guided-tour-steps';
import { RoleEnums } from './role.enums';

const ROLES = [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.BOARD, RoleEnums.TREASURER, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL];
const allOn = () => true;
const allOff = () => false;
const ids = (role: RoleEnums, isEnabled: (feature: TenantFeature) => boolean) => composeGuidedTour(role, isEnabled).map((step) => step.id);
const texts = (text: GuidedTourText): string[] => (typeof text === 'string' ? [text] : Object.values(text));

const END = ['common.notifications', 'common.profile', 'common.help'];
const START = ['common.welcome', 'common.navigation'];

describe('Guided tour catalog', () => {
    it('composes the path of every role with all the features on', () => {
        expect(ids(RoleEnums.SUPER_ADMIN, allOn)).toEqual([...START, 'superAdmin.tenants', 'superAdmin.legalDocuments', 'admin.users', 'admin.tools', ...END]);
        expect(ids(RoleEnums.ADMIN, allOn)).toEqual([...START, 'admin.users', 'admin.calendar', 'admin.inventory', 'finance.overview', 'admin.tools', ...END]);
        expect(ids(RoleEnums.BOARD, allOn)).toEqual([...START, 'admin.users', 'admin.calendar', 'admin.inventory', 'finance.overview', ...END]);
        expect(ids(RoleEnums.TREASURER, allOn)).toEqual([...START, 'finance.overview', 'finance.movements', 'finance.events', ...END]);
        expect(ids(RoleEnums.ARCHIVIST, allOn)).toEqual([...START, 'catalog.manage', 'catalog.instruments', 'calendar.program', 'inventory.mine', ...END]);
        expect(ids(RoleEnums.USER, allOn)).toEqual([...START, 'calendar.availability', 'catalog.tracks', 'inventory.mine', ...END]);
        expect(ids(RoleEnums.USER_EXTERNAL, allOn)).toEqual([...START, 'calendar.availability', 'catalog.tracks', 'inventory.mine', ...END]);
    });

    it('composes the path of every role with all the features off', () => {
        expect(ids(RoleEnums.SUPER_ADMIN, allOff)).toEqual([...START, 'superAdmin.tenants', 'superAdmin.legalDocuments', 'admin.users', 'admin.tools', ...END]);
        expect(ids(RoleEnums.ADMIN, allOff)).toEqual([...START, 'admin.users', 'admin.calendar', 'admin.tools', ...END]);
        expect(ids(RoleEnums.BOARD, allOff)).toEqual([...START, 'admin.users', 'admin.calendar', ...END]);
        expect(ids(RoleEnums.TREASURER, allOff)).toEqual([...START, ...END]);
        expect(ids(RoleEnums.ARCHIVIST, allOff)).toEqual([...START, 'catalog.manage', 'catalog.instruments', 'calendar.program', ...END]);
        expect(ids(RoleEnums.USER, allOff)).toEqual([...START, 'calendar.availability', 'catalog.tracks', ...END]);
        expect(ids(RoleEnums.USER_EXTERNAL, allOff)).toEqual([...START, 'calendar.availability', 'catalog.tracks', ...END]);
    });

    it('never shows a section that is off', () => {
        const allButFinance = (feature: TenantFeature) => feature !== TenantFeature.FINANCE;

        for (const role of ROLES) {
            expect(ids(role, allButFinance).filter((id) => id.startsWith('finance.')))
                .withContext(role)
                .toEqual([]);
        }
    });

    it('keeps the points whose features are on', () => {
        const qrOnly = (feature: TenantFeature) => feature === TenantFeature.INVENTORY || feature === TenantFeature.INVENTORY_QR;
        const inventory = composeGuidedTour(RoleEnums.USER, qrOnly).find((step) => step.id === 'inventory.mine')!;
        const tools = composeGuidedTour(RoleEnums.ADMIN, allOff).find((step) => step.id === 'admin.tools')!;

        expect(inventory.points).toEqual(["Inquadra il QR dell'oggetto per segnalare un guasto"]);
        expect(tools.points).toEqual(['Consegne notifiche: controlla quelle non arrivate']);
    });

    it('limits the calendar feed point of the profile to the members', () => {
        const profile = (role: RoleEnums) => composeGuidedTour(role, allOn).find((step) => step.id === 'common.profile')!.points;

        expect(profile(RoleEnums.USER)).toEqual(['Collega Taurus al calendario del telefono']);
        expect(profile(RoleEnums.USER_EXTERNAL)).toEqual(['Collega Taurus al calendario del telefono']);
        expect(profile(RoleEnums.ADMIN)).toEqual([]);
    });

    it('promises the external user only public events and tracks', () => {
        const external = composeGuidedTour(RoleEnums.USER_EXTERNAL, allOn);
        const user = composeGuidedTour(RoleEnums.USER, allOn);

        expect(external.find((step) => step.id === 'calendar.availability')!.body).toContain('eventi pubblici');
        expect(external.find((step) => step.id === 'catalog.tracks')!.body).toContain('brani pubblici');
        expect(user.find((step) => step.id === 'calendar.availability')!.body).not.toContain('pubblici');
    });

    it('tells the board who manages the administrators', () => {
        const users = (role: RoleEnums) => composeGuidedTour(role, allOn).find((step) => step.id === 'admin.users')!.body;

        expect(users(RoleEnums.BOARD)).toContain("Solo l'Amministratore gestisce gli amministratori.");
        expect(users(RoleEnums.ADMIN)).not.toContain('Solo');
    });

    it('points a page step at the page header below 992 px', () => {
        const users = composeGuidedTour(RoleEnums.ADMIN, allOn).find((step) => step.id === 'admin.users')!;
        const navigation = composeGuidedTour(RoleEnums.ADMIN, allOn).find((step) => step.id === 'common.navigation')!;

        expect(users.anchors).toEqual(['users.create', 'users.header']);
        expect(users.mobileAnchors).toEqual(['users.header']);
        expect(navigation.mobileAnchors).toEqual(['layout.menu', 'layout.menuButton']);
    });

    it('never exceeds ten steps, welcome included', () => {
        for (const role of ROLES) {
            expect(ids(role, allOn).length).withContext(role).toBeLessThanOrEqual(GUIDED_TOUR_MAX_STEPS);
            expect(ids(role, allOff).length).withContext(role).toBeLessThanOrEqual(GUIDED_TOUR_MAX_STEPS);
        }
    });

    it('keeps identifiers and texts within the limits of the card', () => {
        const unique = new Set(GUIDED_TOUR_STEPS.map((step) => step.id));
        expect(unique.size).toBe(GUIDED_TOUR_STEPS.length);

        for (const step of GUIDED_TOUR_STEPS) {
            expect(step.id)
                .withContext(step.id)
                .toMatch(/^[a-z][a-zA-Z0-9]*(\.[a-z][a-zA-Z0-9]*)+$/);
            expect(step.id.length).withContext(step.id).toBeLessThanOrEqual(64);
            expect(step.title.length).withContext(step.id).toBeLessThanOrEqual(40);
            texts(step.body).forEach((body) => expect(body.length).withContext(step.id).toBeLessThanOrEqual(180));
            expect((step.points ?? []).length)
                .withContext(step.id)
                .toBeLessThanOrEqual(3);
            (step.points ?? []).forEach((point) => expect(point.text.length).withContext(step.id).toBeLessThanOrEqual(60));
            expect(step.introducedIn).withContext(step.id).toBeGreaterThan(0);
        }
    });

    it('never says tenant to the roles of an organization', () => {
        for (const step of GUIDED_TOUR_STEPS.filter((candidate) => !candidate.roles.every((role) => role === RoleEnums.SUPER_ADMIN))) {
            texts(step.body).forEach((body) => expect(body.toLowerCase()).withContext(step.id).not.toContain('tenant'));
        }
    });
});
