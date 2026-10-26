package org.example.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.domain.Transfer;
import org.example.domain.TransferStatus;

import java.time.Instant;

public final class TransferDtos {

    public static final long MAX_AMOUNT = 5_000_000L;

    private TransferDtos() {
    }

    public record TransferRequest(
            @NotBlank String fromWallet,
            @NotBlank String toWallet,
            @NotNull @Min(1) @Max(MAX_AMOUNT) Long amount,
            @NotBlank @Size(min = 1, max = 64) String reference) {
    }

    public record TransferResponse(String transferId, TransferStatus status, long fromAvailableAfter, Instant createdAt) {
    }

    public record ReversalRequest(@Size(max = 255) String reason) {
    }

    public record ReversalResponse(String transferId, String reversalId, TransferStatus status, long amount) {
        public static ReversalResponse from(Transfer t) {
            return new ReversalResponse(t.getId(), t.getReversalId(), t.getStatus(), t.getAmount());
        }
    }
}
