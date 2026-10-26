package org.example.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.api.TransferDtos.ReversalRequest;
import org.example.api.TransferDtos.ReversalResponse;
import org.example.api.TransferDtos.TransferRequest;
import org.example.api.TransferDtos.TransferResponse;
import org.example.service.IdempotencyService;
import org.example.service.TransferService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/mpesa/v1/p2p/transfers")
@Tag(name = "P2P transfers")
public class TransferController {

    private final TransferService transfers;
    private final IdempotencyService idempotency;

    @PostMapping
    @Operation(summary = "Move money between two wallets")
    public ResponseEntity<String> transfer(@RequestHeader("Idempotency-Key") String key,
                                           @Valid @RequestBody TransferRequest request) {
        return idempotency.run(key, "p2p.transfer", request, HttpStatus.CREATED, TransferResponse.class,
                () -> transfers.transfer(request.fromWallet(), request.toWallet(), request.amount(), request.reference()));
    }

    @PostMapping("/{id}/reversal")
    @Operation(summary = "Reverse a completed transfer, once")
    public ReversalResponse reverse(@PathVariable String id, @RequestBody(required = false) @Valid ReversalRequest request) {
        return ReversalResponse.from(transfers.reverse(id, request == null ? null : request.reason()));
    }
}
