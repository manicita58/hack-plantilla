import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { apiError } from '../../core/api';
import { Ai, AiStatus } from './ai.service';

interface Turno {
  role: 'user' | 'assistant';
  content: string;
}

@Component({
  selector: 'app-chat-page',
  imports: [FormsModule],
  templateUrl: './chat-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatPage implements OnInit {
  private readonly ai = inject(Ai);

  readonly mensajes = signal<Turno[]>([]);
  readonly estado = signal<AiStatus | null>(null);
  readonly enviando = signal(false);
  readonly error = signal('');

  texto = '';
  private conversacion: string | null = null;

  async ngOnInit(): Promise<void> {
    try {
      this.estado.set(await this.ai.status());
    } catch (err) {
      this.error.set(`No se pudo leer el estado del módulo: ${apiError(err)}`);
    }
  }

  nueva(): void {
    this.conversacion = null;
    this.mensajes.set([]);
    this.error.set('');
  }

  async enviar(event: Event): Promise<void> {
    event.preventDefault();
    const mensaje = this.texto.trim();
    if (!mensaje || this.enviando()) {
      return;
    }

    this.texto = '';
    this.error.set('');
    this.enviando.set(true);
    // Se pinta el turno del usuario y un turno vacío de la IA que se va
    // llenando con cada token: eso es lo que se ve "escribiendo".
    this.mensajes.update((turnos) => [...turnos, { role: 'user', content: mensaje }, { role: 'assistant', content: '' }]);

    try {
      this.conversacion = await this.ai.chat(this.conversacion, mensaje, (token) => {
        this.mensajes.update((turnos) => {
          const copia = [...turnos];
          const ultimo = copia.length - 1;
          copia[ultimo] = { ...copia[ultimo], content: copia[ultimo].content + token };
          return copia;
        });
      });
    } catch (err) {
      this.error.set(`El chat falló: ${apiError(err)}`);
      // Se saca el turno vacío para no dejar una burbuja en blanco.
      this.mensajes.update((turnos) => turnos.filter((t, i) => i !== turnos.length - 1 || t.content !== ''));
    } finally {
      this.enviando.set(false);
    }
  }
}
