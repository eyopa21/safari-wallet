package org.example.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.api.WalletPayoutDtos.BankCallback;
import org.example.service.BankCallbackService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/mpesa/v1/callbacks")
@Tag(name = "Bank callbacks")
public class BankCallbackController {

    private final BankCallbackService callbacks;

    @PostMapping("/bank")
    @Operation(summary = "Outcome callback from the bank")
    public void bank(@Valid @RequestBody BankCallback event) {
        callbacks.handle(event.eventId(), event.payoutId(), event.bankRef(), event.status());
    }
}
