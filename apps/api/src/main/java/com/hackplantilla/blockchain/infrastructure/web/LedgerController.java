package com.hackplantilla.blockchain.infrastructure.web;

import com.hackplantilla.blockchain.BlockchainModule;
import com.hackplantilla.blockchain.application.LedgerService;
import com.hackplantilla.blockchain.domain.Block;
import com.hackplantilla.blockchain.domain.Verification;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Adaptador de entrada HTTP. Toda la lógica está en application/domain. */
@RestController
@RequestMapping("/ledger")
@BlockchainModule
public class LedgerController {

    private final LedgerService ledger;

    public LedgerController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @GetMapping
    public List<Block> chain() {
        return ledger.chain();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Block append(@RequestBody NewEntry body) {
        return ledger.append(body.content());
    }

    @GetMapping("/verify")
    public Verification verify() {
        return ledger.verify();
    }

    public record NewEntry(String content) {
    }
}
