package org.example;

import org.example.domain.WalletPayout;
import org.example.domain.WalletPayoutState;
import org.example.service.WalletPayoutService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// the background worker is effectively off here (one run at startup, then never), so claims are ours alone
@TestPropertySource(properties = "bank.worker-interval=3600s")
class WorkerClaimTest extends IntegrationTest {

    @Autowired
    WalletPayoutService payouts;

    @Test
    void aClaimedPayoutIsInvisibleToOthersUntilItsLeaseExpires() throws Exception {
        String w = newWallet(10_000);
        WalletPayout p = payouts.accept(w, "ACC-001", 100);

        List<WalletPayout> first = payouts.claimDue(WalletPayoutState.ACCEPTED, 10);
        assertThat(first).extracting(WalletPayout::getId).contains(p.getId());

        // "another instance" looks right away: the row is leased, so it is not handed out again
        List<WalletPayout> second = payouts.claimDue(WalletPayoutState.ACCEPTED, 10);
        assertThat(second).extracting(WalletPayout::getId).doesNotContain(p.getId());

        // the instance that claimed it dies and never reports back; after the lease (2s in tests) it is free again
        Thread.sleep(2_500);
        List<WalletPayout> third = payouts.claimDue(WalletPayoutState.ACCEPTED, 10);
        assertThat(third).extracting(WalletPayout::getId).contains(p.getId());
    }

    @Test
    void acceptReservesWithoutTouchingBalance() {
        String w = newWallet(10_000);
        payouts.accept(w, "ACC-001", 4_000);
        assertThat(balance(w)).isEqualTo(10_000);
        assertThat(reserved(w)).isEqualTo(4_000);
        assertLedgerReconciles();   // a reservation is not a ledger movement
    }
}
