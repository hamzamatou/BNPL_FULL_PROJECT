import { Routes } from '@angular/router';
import { LoginComponent } from './login/login.component';
import { AdminPortalComponent } from './admin/admin.component';
import { AdminShellComponent } from './admin/admin-shell/admin-shell.component';
import { NouvelleDemandeComponent } from './commercant/pages/nouvelle-demande/nouvelle-demande.component';
import { MesDemandesComponent } from './commercant/pages/mes-demandes/mes-demandes.component';
import { DemandeDetailComponent } from './commercant/pages/demande-detail/demande-detail.component';
import { ActionClientComponent } from './client/pages/action-client/action-client.component';
import { RoleGuard } from './guards/role.guard';
import { BanqueDemandesComponent } from './banque/pages/banque-demandes/banque-demandes.component';
import { BanquePriseEnChargeComponent } from './banque/pages/banque-prise-en-charge/banque-prise-en-charge.component';
import { BanquePriseEnChargeDetailComponent } from './banque/pages/banque-prise-en-charge-detail/banque-prise-en-charge-detail.component';
import { ActivateAccountComponent } from './activate-account/activate-account.component';
import { UserDetailComponent } from './admin/user-detail/user-detail.component';
import { OtpVerifyComponent } from './otp-verify/otp-verify.component';
import { ReportingPilotageComponent } from './admin/pages/reporting-pilotage/reporting-pilotage.component';
import { BanquePilotageComponent } from './banque/pages/banque-pilotage/banque-pilotage.component';
import { BanqueMesDemandesComponent } from './banque/pages/banque-mes-demandes/banque-mes-demandes.component';
import { AdminDashboardComponent } from './admin/pages/admin-dashboard/admin-dashboard.component';
import { AdminDemandesListComponent } from './admin/pages/admin-demandes-list/admin-demandes-list.component';
import { AdminTraceabiliteDemandeComponent } from './admin/pages/admin-traceabilite-demande/admin-traceabilite-demande.component';
import { AdminUtilisateursCreerComponent } from './admin/pages/admin-utilisateurs-creer/admin-utilisateurs-creer.component';
import { AdminUtilisateursAccesComponent } from './admin/pages/admin-utilisateurs-acces/admin-utilisateurs-acces.component';

export const routes: Routes = [

  { path: '',                        redirectTo: '/login', pathMatch: 'full' },
  { path: 'login',                   component: LoginComponent },
  { path: 'verify-otp',              component: OtpVerifyComponent },
  { path: 'activate-account/:token', component: ActivateAccountComponent },
  { path: 'action-client',           component: ActionClientComponent },

  {
    path: 'admin',
    component: AdminShellComponent,
    canActivate: [RoleGuard],
    data: { role: 'ADMIN' },
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: AdminDashboardComponent },
      { path: 'demandes', redirectTo: 'demandes/en-cours', pathMatch: 'full' },
      {
        path: 'demandes/en-cours',
        component: AdminDemandesListComponent,
        data: { mode: 'en-cours' },
      },
      {
        path: 'demandes/archivees',
        component: AdminDemandesListComponent,
        data: { mode: 'archivees' },
      },
      {
        path: 'demandes/:demandeId/traceabilite',
        component: AdminTraceabiliteDemandeComponent,
      },
      {
        path: 'traceabilite',
        redirectTo: 'demandes/en-cours',
        pathMatch: 'full',
      },
      { path: 'reporting', redirectTo: 'demandes/en-cours', pathMatch: 'full' },
      { path: 'utilisateurs', component: AdminPortalComponent },
      { path: 'utilisateurs/creer', component: AdminUtilisateursCreerComponent },
      { path: 'utilisateurs/acces', component: AdminUtilisateursAccesComponent },
      { path: 'user/:id', component: UserDetailComponent },
    ],
  },

  {
    path: 'commercant',
    component: NouvelleDemandeComponent,
    canActivate: [RoleGuard],
    data: { role: 'COMMERCANT' },
  },
  {
    path: 'mes-demandes',
    component: MesDemandesComponent,
    canActivate: [RoleGuard],
    data: { role: 'COMMERCANT' },
  },
  {
    path: 'mes-demandes/:id',
    component: DemandeDetailComponent,
    canActivate: [RoleGuard],
    data: { role: 'COMMERCANT' },
  },
  { path: 'banque', redirectTo: '/banque/demandes', pathMatch: 'full' },
  {
    path: 'banque/demandes',
    component: BanqueDemandesComponent,
    canActivate: [RoleGuard],
    data: { role: 'ANALYSTE_BANCAIRE' },
  },
  {
    path: 'banque/affectees',
    component: BanquePriseEnChargeComponent,
    canActivate: [RoleGuard],
    data: { role: 'ANALYSTE_BANCAIRE' },
  },
  {
    path: 'banque/affectees/:id',
    component: BanquePriseEnChargeDetailComponent,
    canActivate: [RoleGuard],
    data: { role: 'ANALYSTE_BANCAIRE' },
  },
  {
    path: 'banque/pilotage',
    component: BanquePilotageComponent,
    canActivate: [RoleGuard],
    data: { role: 'ANALYSTE_BANCAIRE' },
  },
  {
    path: 'banque/mes-demandes',
    component: BanqueMesDemandesComponent,
    canActivate: [RoleGuard],
    data: { role: 'ANALYSTE_BANCAIRE' },
  },

  { path: '**', redirectTo: 'login' },
];
