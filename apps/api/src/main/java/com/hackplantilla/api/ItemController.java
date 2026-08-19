package com.hackplantilla.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD mínimo de ejemplo: sirve para verificar que la DB responde de verdad
 * (el /health de actuator no prueba nada del negocio). Borralo al arrancar.
 */
@RestController
@RequestMapping("/items")
public class ItemController {

    private final ItemRepository repository;
    private final Chain chain;

    public ItemController(ItemRepository repository, Chain chain) {
        this.repository = repository;
        this.chain = chain;
    }

    @GetMapping
    public List<Item> list() {
        return repository.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Item create(@RequestBody NewItem body) {
        // No guarda suelto: cada item entra como bloque encadenado al anterior.
        return chain.append(body.name());
    }

    public record NewItem(String name) {
    }
}
