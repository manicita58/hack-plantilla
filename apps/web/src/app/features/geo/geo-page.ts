import { DecimalPipe } from '@angular/common';
import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import * as L from 'leaflet';

import { apiError } from '../../core/api';
import { CategoryCount, Geo, GeoFeature } from './geo.service';

@Component({
  selector: 'app-geo-page',
  imports: [FormsModule, DecimalPipe],
  templateUrl: './geo-page.html',
  styles: `
    .mapa { height: 26rem; width: 100%; }
    /* Leaflet pone su propio fondo blanco; en oscuro queda un flash feo. */
    :host ::ng-deep .leaflet-container { background: var(--hover); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GeoPage implements AfterViewInit, OnDestroy {
  private readonly geo = inject(Geo);
  private readonly contenedor = viewChild.required<ElementRef<HTMLDivElement>>('mapa');

  readonly visibles = signal<GeoFeature[]>([]);
  readonly stats = signal<CategoryCount[]>([]);
  readonly agregando = signal(false);
  readonly modoCercania = signal(false);
  readonly error = signal('');

  nombre = '';
  categoria = 'demo';

  private mapa?: L.Map;
  private capa?: L.GeoJSON;

  ngAfterViewInit(): void {
    this.mapa = L.map(this.contenedor().nativeElement).setView([4.61, -74.07], 13);

    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '© OpenStreetMap',
    }).addTo(this.mapa);

    // circleMarker y no marker: el ícono por defecto de Leaflet apunta a PNGs
    // por ruta relativa al CSS y se rompe al empaquetar. Un círculo no necesita
    // assets y además se colorea con el tema.
    this.capa = L.geoJSON(undefined, {
      pointToLayer: (_feature, latlng) =>
        L.circleMarker(latlng, { radius: 7, weight: 2, color: '#2563eb', fillOpacity: 0.35 }),
      onEachFeature: (feature, layer) => {
        const props = feature.properties as GeoFeature['properties'];
        layer.bindPopup(`<strong>${props.name}</strong><br>${props.category}`);
      },
    }).addTo(this.mapa);

    this.mapa.on('moveend', () => void this.cargar());
    this.mapa.on('click', (evento: L.LeafletMouseEvent) => void this.clickEnMapa(evento));

    void this.cargar();
    void this.cargarStats();
  }

  ngOnDestroy(): void {
    this.mapa?.remove();
  }

  tituloLista(): string {
    return this.modoCercania() ? 'Cerca del centro del mapa' : 'En esta vista';
  }

  /** Pide solo lo que entra en pantalla: el bbox de Leaflet ya viene en el orden que espera el back. */
  async cargar(): Promise<void> {
    if (!this.mapa) {
      return;
    }
    this.modoCercania.set(false);
    try {
      const coleccion = await this.geo.features(this.mapa.getBounds().toBBoxString());
      this.pintar(coleccion.features);
      this.error.set('');
    } catch (err) {
      this.error.set(`No se pudo leer el mapa: ${apiError(err)}`);
    }
  }

  async cercaDelCentro(): Promise<void> {
    if (!this.mapa) {
      return;
    }
    const centro = this.mapa.getCenter();
    try {
      const coleccion = await this.geo.near(centro.lng, centro.lat, 2000);
      this.pintar(coleccion.features);
      this.modoCercania.set(true);
      this.error.set('');
    } catch (err) {
      this.error.set(`La búsqueda por cercanía falló: ${apiError(err)}`);
    }
  }

  alternarAgregar(): void {
    this.agregando.update((activo) => !activo);
  }

  centrar(feature: GeoFeature): void {
    const coords = feature.geometry.coordinates;
    if (feature.geometry.type === 'Point' && Array.isArray(coords)) {
      this.mapa?.setView([Number(coords[1]), Number(coords[0])], 16);
    }
  }

  async borrar(feature: GeoFeature): Promise<void> {
    try {
      await this.geo.remove(feature.properties.id);
      await this.cargar();
      await this.cargarStats();
    } catch (err) {
      this.error.set(`No se pudo borrar: ${apiError(err)}`);
    }
  }

  private async clickEnMapa(evento: L.LeafletMouseEvent): Promise<void> {
    if (!this.agregando()) {
      return;
    }
    const nombre = this.nombre.trim();
    if (!nombre) {
      this.error.set('Ponele un nombre antes de marcar el punto.');
      return;
    }
    try {
      await this.geo.create(nombre, this.categoria.trim() || 'demo', evento.latlng.lng, evento.latlng.lat);
      this.nombre = '';
      this.agregando.set(false);
      await this.cargar();
      await this.cargarStats();
    } catch (err) {
      this.error.set(`No se pudo crear: ${apiError(err)}`);
    }
  }

  private async cargarStats(): Promise<void> {
    try {
      this.stats.set(await this.geo.stats());
    } catch {
      // El panel de stats es accesorio: si falla, no se le arruina el mapa al usuario.
    }
  }

  private pintar(features: GeoFeature[]): void {
    this.visibles.set(features);
    this.capa?.clearLayers();
    if (features.length) {
      this.capa?.addData({ type: 'FeatureCollection', features } as never);
    }
  }
}
