package com.hackplantilla.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** El endpoint que mira el ícono de verificado del front. */
@RestController
@RequestMapping("/chain")
public class ChainController {

    private final Chain chain;

    public ChainController(Chain chain) {
        this.chain = chain;
    }

    @GetMapping("/verify")
    public Chain.Verification verify() {
        return chain.verify();
    }
}
