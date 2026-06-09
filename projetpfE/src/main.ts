import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { bootstrapApplication } from '@angular/platform-browser';
import { LOCALE_ID, provideZoneChangeDetection, importProvidersFrom } from '@angular/core';
import { provideRouter } from '@angular/router';
import { HttpClientModule } from '@angular/common/http';

import { AppComponent } from './app/app.component';
import { routes } from './app/app.routes';

registerLocaleData(localeFr, 'fr-FR');

bootstrapApplication(AppComponent, {
  providers: [
    provideRouter(routes),
    provideZoneChangeDetection({ eventCoalescing: true }),
    importProvidersFrom(HttpClientModule),
    { provide: LOCALE_ID, useValue: 'fr-FR' },
  ],
})
.catch(err => console.error(err));