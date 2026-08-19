import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { apiError } from '../../core/api';
import { Ai, Answer, DocumentSummary } from './ai.service';

@Component({
  selector: 'app-rag-page',
  imports: [FormsModule, DecimalPipe],
  templateUrl: './rag-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RagPage implements OnInit {
  private readonly ai = inject(Ai);

  readonly documentos = signal<DocumentSummary[]>([]);
  readonly ultimaIngesta = signal<DocumentSummary | null>(null);
  readonly respuesta = signal<Answer | null>(null);
  readonly indexando = signal(false);
  readonly preguntando = signal(false);
  readonly error = signal('');

  titulo = '';
  contenido = '';
  pregunta = '';

  ngOnInit(): void {
    void this.cargar();
  }

  async cargar(): Promise<void> {
    try {
      this.documentos.set(await this.ai.documents());
    } catch (err) {
      this.error.set(`No se pudieron leer los documentos: ${apiError(err)}`);
    }
  }

  async indexar(event: Event): Promise<void> {
    event.preventDefault();
    if (!this.titulo.trim() || !this.contenido.trim()) {
      this.error.set('Hacen falta título y contenido.');
      return;
    }
    this.indexando.set(true);
    this.error.set('');
    try {
      this.ultimaIngesta.set(await this.ai.ingest(this.titulo.trim(), this.contenido));
      this.contenido = '';
      await this.cargar();
    } catch (err) {
      this.error.set(`No se pudo indexar: ${apiError(err)}`);
    } finally {
      this.indexando.set(false);
    }
  }

  async olvidar(titulo: string): Promise<void> {
    try {
      await this.ai.forget(titulo);
      await this.cargar();
    } catch (err) {
      this.error.set(`No se pudo borrar: ${apiError(err)}`);
    }
  }

  async preguntar(event: Event): Promise<void> {
    event.preventDefault();
    const pregunta = this.pregunta.trim();
    if (!pregunta) {
      return;
    }
    this.preguntando.set(true);
    this.error.set('');
    try {
      this.respuesta.set(await this.ai.ask(pregunta));
    } catch (err) {
      this.error.set(`La consulta falló: ${apiError(err)}`);
    } finally {
      this.preguntando.set(false);
    }
  }
}
