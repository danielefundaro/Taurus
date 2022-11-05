import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSortModule } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { FlexLayoutModule } from '@angular/flex-layout';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatSelectModule } from '@angular/material/select';
import { AngularEditorModule } from '@kolkov/angular-editor';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { NgxFileDropModule } from 'ngx-file-drop';
import { MatDialogModule } from '@angular/material/dialog';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { MainTranslateModule } from 'src/app/main-translate/main-translate.module';
import { AlbumEditComponent } from '../album-edit/album-edit.component';
import { AlbumsComponent } from '../albums/albums.component';
import { InstrumentsComponent } from '../instruments/instruments.component';
import { PieceEditComponent } from '../piece-edit/piece-edit.component';
import { PiecesComponent } from '../pieces/pieces.component';
import { InstrumentEditComponent } from '../instrument-edit/instrument-edit.component';
import { DeleteDialogComponent } from '../../components/delete-dialog/delete-dialog.component';
import { NoDataComponent } from '../../components/no-data/no-data.component';
import { ViewMediaDialogComponent } from '../../components/view-media-dialog/view-media-dialog.component';
import { FileDropComponent } from '../../components/file-drop/file-drop.component';
import { ButtonsActionsComponent } from '../../components/buttons-actions/buttons-actions.component';
import { NotFoundComponent } from '../not-found/not-found.component';
import { PreviewComponent } from '../preview/preview.component';


@NgModule({
    declarations: [
        AlbumEditComponent,
        AlbumsComponent,
        InstrumentsComponent,
        PieceEditComponent,
        PiecesComponent,
        InstrumentEditComponent,
        DeleteDialogComponent,
        NoDataComponent,
        ViewMediaDialogComponent,
        FileDropComponent,
        ButtonsActionsComponent,
        NotFoundComponent,
        PreviewComponent,
    ],
    imports: [
        CommonModule,
        MatTableModule,
        MatPaginatorModule,
        MatSortModule,
        MatFormFieldModule,
        MatInputModule,
        MatButtonModule,
        MatIconModule,
        MatTooltipModule,
        DragDropModule,
        MainTranslateModule,
        FlexLayoutModule,
        ReactiveFormsModule,
        MatSelectModule,
        AngularEditorModule,
        MatCardModule,
        MatProgressSpinnerModule,
        NgxFileDropModule,
        FormsModule,
        MatDialogModule,
        MatDatepickerModule,
        MatNativeDateModule,
        MatAutocompleteModule,
    ]
})
export class DefaultModule { }
