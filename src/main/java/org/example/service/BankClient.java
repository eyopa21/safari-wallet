package org.example.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class BankClient {

    private final RestClient bankRestClient;

    public record SubmitResult(String result, String bankRef, String reason) {
    }

    public record StatusResult(String status) {
    }

    // bank is idempotent on payout_id, so calling this twice for the same payout is safe
    public SubmitResult submit(String payoutId, String bankAccount, long amount) {
        return bankRestClient.post()
                .uri("/bank/payouts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("payout_id", payoutId, "bank_account", bankAccount, "amount", amount))
                .retrieve()
                .body(SubmitResult.class);
    }

    public String status(String payoutId) {
        StatusResult r = bankRestClient.get()
                .uri("/bank/payouts/{id}", payoutId)
                .retrieve()
                .body(StatusResult.class);
        return r == null ? null : r.status();
    }
}
