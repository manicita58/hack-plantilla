import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { apiError } from '../../core/api';
import { Block, Ledger, Verification } from './ledger.service';

type Estado = 'idle' | 'check' | 'ok' | 'bad';

@Component({
  selector: 'app-ledger-page',
  imports: [FormsModule, DatePipe],
  templateUrl: './ledger-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LedgerPage implements OnInit {
  private readonly ledger = inject(Ledger);
  private readonly contrato = viewChild.required<ElementRef<HTMLDialogElement>>('contrato');

  readonly bloques = signal<Block[]>([]);
  readonly verificacion = signal<Verification | null>(null);
  readonly seleccionado = signal<Block | null>(null);
  readonly estado = signal<Estado>('idle');
  readonly error = signal('');
  readonly guardando = signal(false);

  texto = '';

  ngOnInit(): void {
    void this.cargar();
  }

  async cargar(): Promise<void> {
    try {
      this.bloques.set(await this.ledger.chain());
      this.error.set('');
      await this.verificar();
    } catch (err) {
      this.error.set(`No se pudo leer la cadena: ${apiError(err)}`);
      this.estado.set('bad');
    }
  }

  async verificar(): Promise<void> {
    this.estado.set('check');
    try {
      const resultado = await this.ledger.verify();
      this.verificacion.set(resultado);
      this.estado.set(resultado.valid ? 'ok' : 'bad');
    } catch (err) {
      this.verificacion.set(null);
      this.estado.set('bad');
      this.error.set(`No se pudo verificar: ${apiError(err)}`);
    }
  }

  async agregar(event: Event): Promise<void> {
    event.preventDefault();
    const contenido = this.texto.trim();
    if (!contenido) {
      return;
    }
    this.guardando.set(true);
    try {
      await this.ledger.append(contenido);
      this.texto = '';
      await this.cargar();
    } catch (err) {
      this.error.set(`No se pudo sellar: ${apiError(err)}`);
    } finally {
      this.guardando.set(false);
    }
  }

  etiquetaVerificacion(): string {
    const resultado = this.verificacion();
    if (this.estado() === 'check') {
      return 'Verificando…';
    }
    if (!resultado) {
      return 'Verificar cadena';
    }
    return resultado.valid
      ? `Cadena verificada · ${resultado.blocks} ${resultado.blocks === 1 ? 'bloque' : 'bloques'}`
      : `Cadena adulterada en el bloque #${resultado.brokenAt}`;
  }

  /** Un bloque es sospechoso si es el roto o vino después del roto. */
  sospechoso(bloque: Block): boolean {
    const resultado = this.verificacion();
    return !!resultado && !resultado.valid && resultado.brokenAt !== null && bloque.id >= resultado.brokenAt;
  }

  estadoDelBloque(bloque: Block): string {
    const resultado = this.verificacion();
    if (!resultado) {
      return 'sin verificar';
    }
    return this.sospechoso(bloque)
      ? `cadena rota desde el bloque #${resultado.brokenAt}`
      : 'verificado — el hash cuadra con su contenido';
  }

  abrirContrato(bloque: Block): void {
    this.seleccionado.set(bloque);
    this.contrato().nativeElement.showModal();
  }
}
