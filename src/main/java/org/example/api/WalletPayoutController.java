package org.example.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.api.WalletPayoutDtos.AcceptedResponse;
import org.example.api.WalletPayoutDtos.PayoutRequest;
import org.example.api.WalletPayoutDtos.PayoutResponse;
import org.example.service.IdempotencyService;
import org.example.service.WalletPayoutService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/mpesa/v1/payouts")
@Tag(name = "Payouts")
public class WalletPayoutController {

    private final WalletPayoutService payouts;
    private final IdempotencyService idempotency;

    @PostMapping
    @Operation(summary = "Pay out from a wallet to a bank account")
    public ResponseEntity<String> create(@RequestHeader("Idempotency-Key") String key,
                                         @Valid @RequestBody PayoutRequest request) {
        return idempotency.run(key, "payout", request, HttpStatus.ACCEPTED, AcceptedResponse.class,
                () -> AcceptedResponse.from(payouts.accept(request.walletId(), request.bankAccount(), request.amount())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "WalletPayout details")
    public PayoutResponse get(@PathVariable String id) {
        return PayoutResponse.from(payouts.get(id));
    }
}
