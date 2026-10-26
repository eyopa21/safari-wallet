package org.example;

import org.example.api.ApiException;
import org.example.api.ErrorCode;
import org.example.domain.WalletStatus;
import org.example.service.TransferService;
import org.example.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FreezeTest extends IntegrationTest {

    @Autowired
    TransferService transfers;
    @Autowired
    WalletService wallets;

    @Test
    void nothingDebitsAWalletAfterItsFreezeReturned() throws Exception {
        String a = newWallet(1_000_000);
        String b = newWallet(0);
        AtomicBoolean stop = new AtomicBoolean();
        AtomicInteger rejected = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<?>> debitors = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            debitors.add(pool.submit(() -> {
                while (!stop.get()) {
                    try {
                        transfers.transfer(a, b, 1, "spam");
                    } catch (ApiException e) {
                        assertThat(e.getCode()).isEqualTo(ErrorCode.WALLET_NOT_ACTIVE);
                        rejected.incrementAndGet();
                    }
                }
                return null;
            }));
        }
        Thread.sleep(300);   // let the debits pile up on the wallet lock

        long t0 = System.nanoTime();
        wallets.changeStatus(a, WalletStatus.FROZEN, "test");
        long freezeMillis = (System.nanoTime() - t0) / 1_000_000;
        long balanceWhenFrozen = balance(a);

        Thread.sleep(300);   // debitors keep trying against the frozen wallet
        stop.set(true);
        for (Future<?> f : debitors) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(freezeMillis).as("freeze answered under heavy traffic").isLessThan(2_000);
        assertThat(balance(a)).as("no debit committed after the freeze").isEqualTo(balanceWhenFrozen);
        assertThat(rejected.get()).as("debits after the freeze were rejected").isPositive();
        assertThat(balance(a) + balance(b)).isEqualTo(1_000_000);
        assertLedgerReconciles();
    }

    @Test
    void settingTheSameStatusAgainChangesNothing() {
        String a = newWallet(10);
        var first = wallets.changeStatus(a, WalletStatus.FROZEN, "one");
        var changedAt = first.getStatusChangedAt();
        var again = wallets.changeStatus(a, WalletStatus.FROZEN, "two");
        assertThat(again.getStatusChangedAt()).isEqualTo(changedAt);
        assertThat(again.getStatusReason()).isEqualTo("one");
    }
}
