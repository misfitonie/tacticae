import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'calculator', pathMatch: 'full' },
  {
    path: 'calculator',
    loadComponent: () => import('./calculator/calculator.component').then(m => m.CalculatorComponent),
  },
  {
    path: 'import',
    loadComponent: () => import('./import/import.component').then(m => m.ImportComponent),
  },
];
