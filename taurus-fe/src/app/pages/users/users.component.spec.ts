import { TestBed } from '@angular/core/testing';
import Keycloak from 'keycloak-js';
import { of } from 'rxjs';
import { RoleEnums } from '../../constants/role.enums';
import { Users } from '../../module';
import { UsersComponent } from './users.component';

/** Exposes the selection of the list, which the template drives. */
class TestUsersComponent extends UsersComponent {
    selection(): Users[] {
        return this.selectedUsers;
    }

    selectAll(items: Users[]): void {
        this.toggleSelectAll(items);
    }

    allSelected(items: Users[]): boolean {
        return this.isAllSelected(items);
    }

    manageable(item: Users): boolean {
        return this.canManage(item);
    }
}

describe('UsersComponent', () => {
    const member = { id: 1, roles: [RoleEnums.USER] } as unknown as Users;
    const treasurer = { id: 2, roles: [RoleEnums.USER, RoleEnums.TREASURER] } as unknown as Users;
    const administrator = { id: 3, roles: [RoleEnums.USER, RoleEnums.ADMIN] } as unknown as Users;
    const items = [member, treasurer, administrator];

    function listFor(role: RoleEnums): TestUsersComponent {
        TestBed.configureTestingModule({ providers: [{ provide: Keycloak, useValue: { idTokenParsed: { role } } }] });
        const instruments = { getAll: () => of({ content: [], totalElements: 0 }) };
        return TestBed.runInInjectionContext(() => new TestUsersComponent({} as never, instruments as never, {} as never, {} as never, {} as never, {} as never));
    }

    it('keeps the administrators out of what a board member selects', () => {
        const list = listFor(RoleEnums.BOARD);

        expect(list.manageable(member)).toBeTrue();
        expect(list.manageable(administrator)).toBeFalse();

        list.selectAll(items);

        expect(list.selection()).toEqual([member, treasurer]);
        expect(list.allSelected(items)).toBeTrue();

        list.selectAll(items);

        expect(list.selection()).toEqual([]);
    });

    it('lets the administrator select every user', () => {
        const list = listFor(RoleEnums.ADMIN);

        list.selectAll(items);

        expect(list.selection()).toEqual(items);
    });
});
