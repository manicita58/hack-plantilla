import { Routes } from '@angular/router';

/**
 * Un módulo = una ruta con `loadComponent` = un chunk aparte. Desprender un
 * módulo del front es borrar su carpeta en features/ y su línea de acá: nada
 * más lo referencia, y lo que se baja el browser también se achica.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'cadena' },
  {
    path: 'cadena',
    title: 'Cadena de bloques',
    loadComponent: () => import('./features/blockchain/ledger-page').then((m) => m.LedgerPage),
  },
  {
    path: 'ia',
    title: 'Chat con IA',
    loadComponent: () => import('./features/ai/chat-page').then((m) => m.ChatPage),
  },
  {
    path: 'ia/documentos',
    title: 'RAG sobre documentos',
    loadComponent: () => import('./features/ai/rag-page').then((m) => m.RagPage),
  },
  {
    path: 'geo',
    title: 'Geovisor',
    loadComponent: () => import('./features/geo/geo-page').then((m) => m.GeoPage),
  },
  { path: '**', redirectTo: 'cadena' },
];
