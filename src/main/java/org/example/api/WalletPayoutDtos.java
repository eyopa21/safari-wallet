package org.example.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.domain.WalletPayout;
import org.example.domain.WalletPayoutStatus;

import java.time.Instant;

public final class WalletPayoutDtos {

    private WalletPayoutDtos() {
    }

    public record PayoutRequest(
            @NotBlank String walletId,
            @NotBlank @Size(max = 34) String bankAccount,
            @NotNull @Min(1) @Max(TransferDtos.MAX_AMOUNT) Long amount) {
    }

    public record AcceptedResponse(String payoutId, WalletPayoutStatus status) {
        public static AcceptedResponse from(WalletPayout p) {
            return new AcceptedResponse(p.getId(), p.getStatus());
        }
    }

    public record PayoutResponse(String payoutId, String walletId, long amount, WalletPayoutStatus status,
                                 String bankRef, boolean conflict, Instant updatedAt) {
        public static PayoutResponse from(WalletPayout p) {
            return new PayoutResponse(p.getId(), p.getWalletId(), p.getAmount(), p.getStatus(),
                    p.getBankRef(), p.isConflict(), p.getUpdatedAt());
        }
    }

    // what the bank posts to /mpesa/v1/callbacks/bank
    public record BankCallback(@NotBlank String eventId, @NotBlank String payoutId, String bankRef, @NotBlank String status) {
    }
}
