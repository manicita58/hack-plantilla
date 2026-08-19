import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

/**
 * En producción el front vive en `dominio.com` y el back en `api.dominio.com`.
 * En local, `ng serve` corre en :4200 y la API en :8080 (ese origen ya viene en
 * el default de CORS_ORIGINS del back).
 */
export const API_BASE = ['localhost', '127.0.0.1'].includes(location.hostname)
  ? 'http://localhost:8080'
  : `https://api.${location.hostname.replace(/^www\./, '')}`;

/**
 * Token opcional para /ai y las escrituras de /geo (ver ApiTokenFilter en el back).
 * Se guarda a mano desde la consola del browser:
 *   localStorage.setItem('apiToken', 'el-token')
 * No se hardcodea: lo que va en el bundle lo lee cualquiera en el devtools.
 */
export function authHeaders(): Record<string, string> {
  const token = localStorage.getItem('apiToken');
  return token ? { 'X-Api-Token': token } : {};
}

/** Envoltorio mínimo sobre HttpClient: promesas, para usar async/await con signals. */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  get<T>(path: string, params: Record<string, string | number | boolean> = {}): Promise<T> {
    return firstValueFrom(this.http.get<T>(API_BASE + path, { params, headers: authHeaders() }));
  }

  post<T>(path: string, body: unknown): Promise<T> {
    return firstValueFrom(this.http.post<T>(API_BASE + path, body, { headers: authHeaders() }));
  }

  delete<T>(path: string): Promise<T> {
    return firstValueFrom(this.http.delete<T>(API_BASE + path, { headers: authHeaders() }));
  }
}

/** Mensaje legible desde un error de HttpClient (el back manda `{error: "..."}`). */
export function apiError(err: unknown): string {
  const e = err as { error?: { error?: string }; message?: string; status?: number };
  return e?.error?.error ?? (e?.status === 0 ? 'no se pudo contactar la API' : e?.message ?? 'error desconocido');
}
