package org.example.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.api.WalletDtos.LedgerEntryResponse;
import org.example.api.WalletDtos.StatusRequest;
import org.example.api.WalletDtos.StatusResponse;
import org.example.api.WalletDtos.WalletResponse;
import org.example.service.LedgerService;
import org.example.service.WalletService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/mpesa/v1/wallets")
@Tag(name = "Wallets")
public class WalletController {

    private final WalletService wallets;
    private final LedgerService ledger;
    @GetMapping("/{id}")
    @Operation(summary = "Wallet snapshot")
    public WalletResponse get(@PathVariable String id) {
        return WalletResponse.from(wallets.get(id));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Freeze or unfreeze a wallet")
    public StatusResponse setStatus(@PathVariable String id, @Valid @RequestBody StatusRequest request) {
        return StatusResponse.from(wallets.changeStatus(id, request.status(), request.reason()));
    }

    @GetMapping("/{id}/ledger")
    @Operation(summary = "Latest ledger entries of a wallet")
    public List<LedgerEntryResponse> ledger(@PathVariable String id, @RequestParam(defaultValue = "50") int limit) {
        wallets.get(id);
        return ledger.recent(id, Math.min(limit, 500)).stream().map(LedgerEntryResponse::from).toList();
    }
}
