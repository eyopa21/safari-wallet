package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.BankProperties;
import org.example.domain.WalletPayout;
import org.example.domain.WalletPayoutState;
import org.example.domain.WalletPayoutStatus;
import org.example.service.BankClient.SubmitResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

// Runs on every instance. Each tick claims at most as many payouts as there are free bank threads,
// so a claimed row is always being worked on and never waits in a queue past its claim time.
// Rows are claimed with "for update skip locked", so a payout accepted by an instance that died
// is picked up by another one.
@Component
@Slf4j
@ConditionalOnProperty(name = "bank.worker-enabled", havingValue = "true", matchIfMissing = true)
public class WalletPayoutWorker {

    private final WalletPayoutService payouts;
    private final BankClient bank;
    private final ThreadPoolTaskExecutor bankExecutor;
    private final Semaphore freeSlots;

    public WalletPayoutWorker(WalletPayoutService payouts, BankClient bank, ThreadPoolTaskExecutor bankExecutor,
                              BankProperties props) {
        this.payouts = payouts;
        this.bank = bank;
        this.bankExecutor = bankExecutor;
        this.freeSlots = new Semaphore(props.workerThreads());
    }

    @Scheduled(fixedDelayString = "${bank.worker-interval}")
    public void tick() {
        try {
            dispatch(WalletPayoutState.ACCEPTED);
            dispatch(WalletPayoutState.SUBMITTED);
        } catch (Exception e) {
            log.error("payout worker tick failed: {}", e.getMessage(), e);
        }
    }

    private void dispatch(WalletPayoutState state) {
        int free = freeSlots.availablePermits();
        if (free == 0) {
            return;
        }
        for (WalletPayout p : payouts.claimDue(state, free)) {
            if (!freeSlots.tryAcquire()) {
                payouts.retryLater(p.getId());   // lost the slot to the other dispatch, let it go
                continue;
            }
            bankExecutor.execute(() -> {
                try {
                    if (state == WalletPayoutState.ACCEPTED) {
                        submit(p);
                    } else {
                        poll(p);
                    }
                } catch (Exception e) {
                    log.error("payout {} step failed: {}", p.getId(), e.getMessage(), e);
                    payouts.retryLater(p.getId());
                } finally {
                    freeSlots.release();
                }
            });
        }
    }

    private void submit(WalletPayout p) {
        SubmitResult r;
        try {
            r = bank.submit(p.getId(), p.getBankAccount(), p.getAmount());
        } catch (Exception e) {
            // unknown whether the bank took it: never mark FAILED here, just ask again later
            log.warn("submit {} to bank failed: {}", p.getId(), e.getMessage());
            payouts.retryLater(p.getId());
            return;
        }
        if ("ACCEPTED".equals(r.result())) {
            payouts.submitted(p.getId(), r.bankRef());
        } else {
            payouts.finish(p.getId(), WalletPayoutStatus.FAILED, null, "bank rejected: " + r.reason());
        }
    }

    private void poll(WalletPayout p) {
        String status;
        try {
            status = bank.status(p.getId());
        } catch (Exception e) {
            log.warn("status of {} from bank failed: {}", p.getId(), e.getMessage());
            payouts.retryLater(p.getId());
            return;
        }
        WalletPayoutStatus finalStatus = status == null ? null : WalletPayoutService.toFinalStatus(status);
        if (finalStatus != null) {
            payouts.finish(p.getId(), finalStatus, p.getBankRef(), "bank status poll");
        } else if ("NOT_FOUND".equals(status)) {
            payouts.resubmit(p.getId());
        } else {
            payouts.retryLater(p.getId());
        }
    }
}
