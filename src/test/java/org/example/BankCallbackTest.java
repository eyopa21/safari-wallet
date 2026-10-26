package org.example;

import org.example.domain.WalletPayout;
import org.example.service.BankCallbackService;
import org.example.service.WalletPayoutService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

class BankCallbackTest extends IntegrationTest {

    @Autowired
    WalletPayoutService payouts;
    @Autowired
    BankCallbackService callbacks;

    @Test
    void threeConcurrentCopiesOfTheSameCallbackFinaliseOnce() throws Exception {
        String w = newWallet(10_000);
        WalletPayout p = payouts.accept(w, "ACC-001", 2_500);
        assertThat(reserved(w)).isEqualTo(2_500);
        payouts.submitted(p.getId(), "BR1");
        String eventId = "evt-" + p.getId();

        List<Callable<Void>> copies = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            copies.add(() -> {
                callbacks.handle(eventId, p.getId(), "BR1", "SUCCEEDED");
                return null;
            });
        }
        runConcurrently(copies);

        assertThat(count("select count(*) from bank_event where event_id = ?", eventId)).isEqualTo(1);
        assertThat(payouts.get(p.getId()).getStatus().name()).isEqualTo("SUCCEEDED");
        assertThat(balance(w)).as("captured exactly once").isEqualTo(7_500);
        assertThat(reserved(w)).isZero();
        assertThat(count("select count(*) from ledger_entry where source_id = ?", p.getId())).isEqualTo(2);
        assertLedgerReconciles();
    }

    @Test
    void callbackBeforeSubmitReturnsStillWins() {
        String w = newWallet(10_000);
        WalletPayout p = payouts.accept(w, "ACC-001", 1_000);
        // bank's callback lands while the payout is still ACCEPTED on our side
        callbacks.handle("evt-early-" + p.getId(), p.getId(), "BR9", "FAILED");
        // then the late submit response arrives
        payouts.submitted(p.getId(), "BR9");

        WalletPayout after = payouts.get(p.getId());
        assertThat(after.getStatus().name()).isEqualTo("FAILED");
        assertThat(after.isDone()).isTrue();
        assertThat(balance(w)).isEqualTo(10_000);
        assertThat(reserved(w)).as("reservation released, not captured").isZero();
    }

    @Test
    void aLaterDisagreeingSignalIsFlaggedNotApplied() {
        String w = newWallet(10_000);
        WalletPayout p = payouts.accept(w, "ACC-001", 1_000);
        callbacks.handle("evt-a-" + p.getId(), p.getId(), "BR1", "SUCCEEDED");
        callbacks.handle("evt-b-" + p.getId(), p.getId(), "BR1", "FAILED");

        WalletPayout after = payouts.get(p.getId());
        assertThat(after.getStatus().name()).isEqualTo("SUCCEEDED");
        assertThat(after.isConflict()).isTrue();
        assertThat(balance(w)).isEqualTo(9_000);
        assertThat(reserved(w)).isZero();
    }
}
