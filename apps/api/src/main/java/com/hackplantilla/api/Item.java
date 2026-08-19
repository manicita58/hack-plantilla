package com.hackplantilla.api;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Entidad de ejemplo, y a la vez bloque de la cadena: `prevHash` la encadena al
 * item anterior y `hash` firma su contenido (ver {@link Chain}).
 * Existe para demostrar que la cadena app -> JPA -> Flyway -> Postgres está
 * enchufada de punta a punta. Borrala cuando arranques el proyecto de verdad.
 */
@Entity
@Table(name = "items")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Truncado a microsegundos porque es lo que guarda TIMESTAMPTZ: si se
    // hashea con nanos, lo que vuelve de la DB ya no da el mismo hash y la
    // verificación falla siempre.
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

    // Nulos en items creados antes de la cadena: esos no son bloques.
    @Column(name = "prev_hash")
    private String prevHash;

    @Column(name = "hash")
    private String hash;

    protected Item() {
        // requerido por JPA
    }

    public Item(String name) {
        this.name = name;
    }

    /** Lo llama {@link Chain#append}: sella el bloque antes de guardarlo. */
    void seal(String prevHash, String hash) {
        this.prevHash = prevHash;
        this.hash = hash;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public String getHash() {
        return hash;
    }
}
