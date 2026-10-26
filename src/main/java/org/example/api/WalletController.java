package org.example.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.api.WalletDtos.StatusRequest;
import org.example.api.WalletDtos.StatusResponse;
import org.example.api.WalletDtos.WalletResponse;
import org.example.service.WalletService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/mpesa/v1/wallets")
@Tag(name = "Wallets")
public class WalletController {

    private final WalletService wallets;
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
}
