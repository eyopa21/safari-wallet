package org.example.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.domain.LedgerEntry;
import org.example.domain.Wallet;
import org.example.domain.WalletStatus;

import java.time.Instant;

public final class WalletDtos {

    private WalletDtos() {
    }

    public record WalletResponse(String walletId, WalletStatus status, long balance, long reserved, long available) {
        public static WalletResponse from(Wallet w) {
            return new WalletResponse(w.getId(), w.getStatus(), w.getBalance(), w.getReserved(), w.available());
        }
    }

    public record StatusRequest(@NotNull WalletStatus status, @Size(max = 255) String reason) {
    }

    public record StatusResponse(String walletId, WalletStatus status, Instant changedAt) {
        public static StatusResponse from(Wallet w) {
            return new StatusResponse(w.getId(), w.getStatus(), w.getStatusChangedAt());
        }
    }

    public record LedgerEntryResponse(long id, String accountId, LedgerEntry.Type type, long amount, Long balanceAfter,
                                      LedgerEntry.Source sourceType, String sourceId, Instant createdAt) {
        public static LedgerEntryResponse from(LedgerEntry e) {
            return new LedgerEntryResponse(e.getId(), e.getAccountId(), e.getType(), e.getAmount(), e.getBalanceAfter(),
                    e.getSourceType(), e.getSourceId(), e.getCreatedAt());
        }
    }
}
