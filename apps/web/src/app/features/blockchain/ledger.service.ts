import { Injectable, inject } from '@angular/core';

import { Api } from '../../core/api';

/** Un bloque tal cual lo devuelve el back. */
export interface Block {
  id: number;
  content: string;
  createdAt: string;
  prevHash: string;
  hash: string;
}

export interface Verification {
  valid: boolean;
  blocks: number;
  brokenAt: number | null;
}

@Injectable({ providedIn: 'root' })
export class Ledger {
  private readonly api = inject(Api);

  chain(): Promise<Block[]> {
    return this.api.get<Block[]>('/ledger');
  }

  append(content: string): Promise<Block> {
    return this.api.post<Block>('/ledger', { content });
  }

  verify(): Promise<Verification> {
    return this.api.get<Verification>('/ledger/verify');
  }
}
