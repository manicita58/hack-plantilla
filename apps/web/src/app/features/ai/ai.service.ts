import { Injectable, inject } from '@angular/core';

import { API_BASE, Api } from '../../core/api';

export interface AiStatus {
  configured: boolean;
  chatModel: string;
  embeddingModel: string;
  documents: DocumentSummary[];
}

export interface DocumentSummary {
  title: string;
  chunks: number;
}

export interface Chunk {
  id: number;
  documentTitle: string;
  content: string;
  score: number;
}

export interface Answer {
  answer: string;
  sources: Chunk[];
}

@Injectable({ providedIn: 'root' })
export class Ai {
  private readonly api = inject(Api);

  status(): Promise<AiStatus> {
    return this.api.get<AiStatus>('/ai/status');
  }

  documents(): Promise<DocumentSummary[]> {
    return this.api.get<DocumentSummary[]>('/ai/documents');
  }

  ingest(title: string, content: string): Promise<DocumentSummary> {
    return this.api.post<DocumentSummary>('/ai/documents', { title, content });
  }

  forget(title: string): Promise<{ deleted: number }> {
    return this.api.delete<{ deleted: number }>(`/ai/documents/${encodeURIComponent(title)}`);
  }

  ask(question: string): Promise<Answer> {
    return this.api.post<Answer>('/ai/ask', { question });
  }

  /**
   * Chat con streaming. Va con fetch y no con HttpClient porque hace falta leer
   * el cuerpo a medida que llega, y no con EventSource porque ese solo hace GET
   * y acá el mensaje viaja en el body.
   */
  async chat(
    conversationId: string | null,
    message: string,
    onToken: (token: string) => void,
  ): Promise<string> {
    const res = await fetch(`${API_BASE}/ai/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ conversationId, message }),
    });
    if (!res.ok || !res.body) {
      throw new Error(`HTTP ${res.status}`);
    }

    const reader = res.body.pipeThrough(new TextDecoderStream()).getReader();
    let buffer = '';
    let conversacion = conversationId ?? '';

    for (;;) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      buffer += value;

      // Los eventos SSE están separados por una línea en blanco; el último
      // pedazo puede venir cortado, así que se guarda para la próxima vuelta.
      const frames = buffer.split('\n\n');
      buffer = frames.pop() ?? '';

      for (const frame of frames) {
        const evento = /^event:\s*(.+)$/m.exec(frame)?.[1]?.trim() ?? 'message';
        const datos = /^data:\s*(.+)$/m.exec(frame)?.[1] ?? '';
        if (!datos) {
          continue;
        }
        const payload = JSON.parse(datos) as { t?: string; conversationId?: string; message?: string };

        if (evento === 'token' && payload.t !== undefined) {
          onToken(payload.t);
        } else if (evento === 'start' && payload.conversationId) {
          conversacion = payload.conversationId;
        } else if (evento === 'error') {
          throw new Error(payload.message ?? 'el modelo falló');
        }
      }
    }
    return conversacion;
  }
}
