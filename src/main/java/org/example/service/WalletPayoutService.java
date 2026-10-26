package org.example.service;

import org.example.domain.Now;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.api.ApiException;
import org.example.api.ErrorCode;
import org.example.config.BankProperties;
import org.example.domain.BankEvent;
import org.example.domain.BankEventRepository;
import org.example.domain.WalletPayout;
import org.example.domain.WalletPayoutRepository;
import org.example.domain.WalletPayoutState;
import org.example.domain.WalletPayoutStatus;
import org.example.domain.Wallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class WalletPayoutService {

    private final WalletPayoutRepository payouts;
    private final BankEventRepository bankEvents;
    private final WalletService wallets;
    private final LedgerService ledger;
    private final BankProperties props;

    // reserve the money and answer; the worker talks to the bank
    @Transactional
    public WalletPayout accept(String walletId, String bankAccount, long amount) {
        Wallet wallet = wallets.lock(walletId);
        WalletService.requireActive(wallet);
        WalletService.requireAvailable(wallet, amount);
        wallet.reserve(amount);
        return payouts.save(new WalletPayout(Ids.next("P"), walletId, bankAccount, amount, Now.instant()));
    }

    @Transactional(readOnly = true)
    public WalletPayout get(String id) {
        return payouts.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.PAYOUT_NOT_FOUND, "payout " + id + " does not exist"));
    }

    // one row per event_id: returns false for a duplicate (concurrent copies hit the primary key)
    @Transactional
    public void recordBankEvent(String eventId, String payoutId, String status) {
        bankEvents.save(new BankEvent(eventId, payoutId, status, Now.instant()));
    }

    // -- worker steps, each its own transaction -------------------------------

    // claim time must cover the slowest bank call (connect + read timeout) with margin
    @Transactional
    public List<WalletPayout> claimDue(WalletPayoutState state, int limit) {
        List<WalletPayout> due = payouts.findDueForUpdate(state.name(), limit);
        Instant until = Now.instant().plus(props.claimTime());
        due.forEach(p -> p.lock(until));
        return due;
    }

    @Transactional
    public void submitted(String id, String bankRef) {
        payouts.lockById(id).filter(p -> !p.isDone())
                .ifPresent(p -> p.submitted(bankRef, Now.instant(), props.pollInterval()));
    }

    @Transactional
    public void retryLater(String id) {
        payouts.lockById(id).filter(p -> !p.isDone())
                .ifPresent(p -> p.retryLater(Now.instant(), backoff(p.getAttempts())));
    }

    @Transactional
    public void resubmit(String id) {
        payouts.lockById(id).filter(p -> !p.isDone()).ifPresent(p -> p.resubmit(Now.instant()));
    }

    // the only place money leaves the wallet or the reservation is released
    @Transactional
    public void finish(String id, WalletPayoutStatus finalStatus, String bankRef, String source) {
        WalletPayout payout = payouts.lockById(id).orElse(null);
        if (payout == null) {
            log.warn("{}: payout {} unknown", source, id);
            return;
        }
        Instant now = Now.instant();
        if (payout.isDone()) {
            if (payout.getStatus() != finalStatus) {
                log.error("{}: payout {} already {} but bank now says {}", source, id, payout.getStatus(), finalStatus);
                payout.markConflict(now);
            }
            return;
        }
        Wallet wallet = wallets.lock(payout.getWalletId());
        if (finalStatus == WalletPayoutStatus.SUCCEEDED) {
            ledger.postPayout(wallet, payout.getAmount(), payout.getId());
        } else {
            wallet.release(payout.getAmount());
        }
        payout.finish(finalStatus, bankRef, now);
        log.info("{}: payout {} {}", source, id, finalStatus);
    }

    static WalletPayoutStatus toFinalStatus(String bankStatus) {
        return switch (bankStatus) {
            case "SUCCEEDED" -> WalletPayoutStatus.SUCCEEDED;
            case "FAILED", "REJECTED" -> WalletPayoutStatus.FAILED;
            default -> null;
        };
    }

    private Duration backoff(int attempts) {
        long seconds = Math.min(props.retryInterval().toSeconds() * (1L << Math.min(attempts, 4)), 30);
        return Duration.ofSeconds(seconds);
    }
}
