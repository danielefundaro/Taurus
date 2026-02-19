import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SelectItem } from 'primeng/api';
import { AutoCompleteCompleteEvent, AutoCompleteModule } from 'primeng/autocomplete';
import { delay, first, firstValueFrom } from 'rxjs';
import { RoleEnums } from '../../../constants';
import { ImportsModule } from '../../../imports';
import { ChildrenEntities, CommonFieldsOpenSearch, CommonOpenSearchCriteria, Instruments, InstrumentsCriteria, Users } from '../../../module';
import { InstrumentsService, ToastService, UsersService } from '../../../service';
import { CommonOpenSearchService } from '../../../service/common-open-search.service';

@Component({
    selector: 'app-user-detail',
    imports: [
        ImportsModule,
        AutoCompleteModule,
    ],
    templateUrl: './detail.component.html',
    styleUrl: './detail.component.scss',
    providers: [
        InstrumentsService
    ],
})
export class DetailComponent implements OnInit {
    protected sortOptions!: SelectItem[];
    protected totalRecords: number = 0;
    protected user: Users = new Users();
    protected cols: string[];
    protected selectedTracks: ChildrenEntities[];

    protected autoFilteredRoles: Array<string>;
    protected autoFilteredInstruments: ChildrenEntities[] = [];
    private instrumentsChildrenEntities: ChildrenEntities[] = [];

    private readonly roles: Array<RoleEnums>;
    private readonly instruments: Instruments[];

    constructor(
        private readonly usersService: UsersService,
        private readonly instrumentsService: InstrumentsService,
        private readonly toastService: ToastService,
        private readonly routeService: ActivatedRoute
    ) {
        this.cols = ["Codice", "Ordine", "Nome"];
        this.selectedTracks = [];

        this.roles = RoleEnums ? Object.values(RoleEnums) : [];
        this.autoFilteredRoles = this.roles;

        this.instruments = [];

        // Preload all instruments
        const instrumentsCriteria: InstrumentsCriteria = { page: 0, sort: ['name.keyword,asc'] };
        this.preloadEntities(this.instrumentsService, instrumentsCriteria, this.instruments);
        this.autoFilteredInstruments = [];
        this.instrumentsChildrenEntities = [];
    }

    ngOnInit() {
        this.routeService.params.pipe(first()).subscribe(params => {
            this.loadElement(params['id']);
        });
    }

    protected save(): void {
        this.usersService.update(this.user.id, this.user).pipe(delay(1000), first()).subscribe({
            next: (user: Users) => {
                this.toastService.success("Successo", "Utente aggiornato con successo");
                this.loadElement(user.id);
            }
        });
    }

    protected filterRoles(event: AutoCompleteCompleteEvent) {
        this.autoFilteredRoles = this.roles.filter(role => role?.toLowerCase()?.includes(event.query.toLowerCase()));
    }

    protected filterInstruments(event: AutoCompleteCompleteEvent) {
        this.autoFilteredInstruments = this.instrumentsChildrenEntities.filter(instrument => instrument.name?.toLowerCase()?.includes(event.query.toLowerCase()));
    }

    protected onReorderInstruments(): void {
        this.user.instruments?.forEach((instrument, i) => instrument.order = i + 1);
    }

    private loadElement(id: string) {
        this.usersService.getById(id).pipe(first()).subscribe({
            next: (user: Users) => {
                this.user = user;
            }
        });
    }

    private preloadEntities<T extends CommonFieldsOpenSearch, T1 extends CommonOpenSearchCriteria>(service: CommonOpenSearchService<T, T1>, criteria: T1, results: T[]): void {
        let page = criteria.page ?? 0;

        service.getAll().pipe(first()).subscribe(async (result) => {
            let totalElements = result.totalElements;
            results.push(...result.content);

            while (totalElements > results.length) {
                criteria.page = ++page;

                const data = await firstValueFrom(service.getAll(criteria));
                results.push(...data.content);
                totalElements = data.totalElements;
            }

            this.instrumentsChildrenEntities = this.instruments.map(instrument => {
                const childrenEntity = new ChildrenEntities();
                childrenEntity.name = instrument.name;
                childrenEntity.index = instrument.id;

                return childrenEntity;
            }) ?? [];

            this.autoFilteredInstruments = this.instrumentsChildrenEntities;
        });
    }
}
