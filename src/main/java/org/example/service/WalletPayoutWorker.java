package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.domain.WalletPayout;
import org.example.domain.WalletPayoutState;
import org.example.domain.WalletPayoutStatus;
import org.example.service.BankClient.SubmitResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Runs on every instance. Rows are claimed with "for update skip locked", so a payout accepted by
// an instance that died is simply picked up by another one.
@Component
@Slf4j
@RequiredArgsConstructor
public class WalletPayoutWorker {

    private final WalletPayoutService payouts;
    private final BankClient bank;

    @Scheduled(fixedDelayString = "${bank.worker-interval}")
    public void run() {
        try {
            for (WalletPayout p : payouts.claimDue(WalletPayoutState.ACCEPTED)) {
                submit(p);
            }
            for (WalletPayout p : payouts.claimDue(WalletPayoutState.SUBMITTED)) {
                poll(p);
            }
        } catch (Exception e) {
            log.error("payout worker failed: {}", e.getMessage(), e);
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
