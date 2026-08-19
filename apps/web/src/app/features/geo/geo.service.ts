import { Injectable, inject } from '@angular/core';

import { Api } from '../../core/api';

/** GeoJSON tal cual: lo que devuelve el back se le pasa a Leaflet sin traducir. */
export interface FeatureCollection {
  type: 'FeatureCollection';
  features: GeoFeature[];
}

export interface GeoFeature {
  type: 'Feature';
  id: number;
  // Geometría GeoJSON cruda; se tipa laxo a propósito porque la tabla
  // guarda puntos, líneas y polígonos en la misma columna.
  geometry: { type: string; coordinates: unknown };
  properties: {
    id: number;
    name: string;
    category: string;
    createdAt: string;
    distanceMeters?: number;
    [extra: string]: unknown;
  };
}

export interface CategoryCount {
  category: string;
  features: number;
}

@Injectable({ providedIn: 'root' })
export class Geo {
  private readonly api = inject(Api);

  /** `bbox` va como minLon,minLat,maxLon,maxLat — el mismo string que da Leaflet. */
  features(bbox?: string, category?: string): Promise<FeatureCollection> {
    const params: Record<string, string> = {};
    if (bbox) {
      params['bbox'] = bbox;
    }
    if (category) {
      params['category'] = category;
    }
    return this.api.get<FeatureCollection>('/geo/features', params);
  }

  near(lon: number, lat: number, meters: number): Promise<FeatureCollection> {
    return this.api.get<FeatureCollection>('/geo/features/near', { lon, lat, meters });
  }

  create(name: string, category: string, lon: number, lat: number): Promise<GeoFeature> {
    return this.api.post<GeoFeature>('/geo/features', {
      name,
      category,
      geometry: { type: 'Point', coordinates: [lon, lat] },
    });
  }

  remove(id: number): Promise<void> {
    return this.api.delete<void>(`/geo/features/${id}`);
  }

  stats(): Promise<CategoryCount[]> {
    return this.api.get<CategoryCount[]>('/geo/stats');
  }
}
