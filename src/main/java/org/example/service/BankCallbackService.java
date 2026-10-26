package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.domain.WalletPayoutStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class BankCallbackService {

    private final WalletPayoutService payouts;

    public void handle(String eventId, String payoutId, String bankRef, String status) {
        try {
            payouts.recordBankEvent(eventId, payoutId, status);
        } catch (DataIntegrityViolationException e) {
            log.info("duplicate bank event {} for payout {}", eventId, payoutId);
            return;
        }
        WalletPayoutStatus finalStatus = WalletPayoutService.toFinalStatus(status);
        if (finalStatus == null) {
            log.warn("bank event {} has status {}, ignored", eventId, status);
            return;
        }
        payouts.finish(payoutId, finalStatus, bankRef, "callback " + eventId);
    }
}
