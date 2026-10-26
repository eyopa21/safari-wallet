package org.example;

import org.example.api.ApiException;
import org.example.api.ErrorCode;
import org.example.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

class TransferConcurrencyTest extends IntegrationTest {

    @Autowired
    TransferService transfers;

    @Test
    void moneyIsConservedUnderConcurrentOppositeTransfers() throws Exception {
        String a = newWallet(50_000);
        String b = newWallet(50_000);

        // 100 transfers in both directions at once, amounts chosen so some will run out of funds
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String from = i % 2 == 0 ? a : b;
            String to = i % 2 == 0 ? b : a;
            long amount = 1_000 + i * 10;
            tasks.add(() -> {
                try {
                    transfers.transfer(from, to, amount, "load");
                    return "ok";
                } catch (ApiException e) {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
                    return "nsf";
                }
            });
        }
        List<String> results = runConcurrently(tasks);
        long ok = results.stream().filter("ok"::equals).count();

        assertThat(balance(a) + balance(b)).isEqualTo(100_000);
        assertThat(balance(a)).isNotNegative();
        assertThat(balance(b)).isNotNegative();
        assertThat(count("select count(*) from transfer where from_wallet in (?, ?)", a, b)).isEqualTo(ok);
        assertThat(count("select count(*) from ledger_entry where source_type = 'TRANSFER' and account_id in (?, ?)", a, b)).isEqualTo(ok * 2);
        assertLedgerReconciles();
    }

    @Test
    void noObserverSeesDebitWithoutCredit() throws Exception {
        String a = newWallet(10_000);
        String b = newWallet(0);
        // readers hammer both balances while transfers run; the sum must never be off
        List<Callable<Long>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> {
                transfers.transfer(a, b, 100, "x");
                return -1L;
            });
            tasks.add(() -> jdbc.queryForObject(
                    "select sum(balance) from wallet where id in (?, ?)", Long.class, a, b));
        }
        List<Long> sums = runConcurrently(tasks).stream().filter(v -> v >= 0).toList();
        assertThat(sums).allMatch(s -> s == 10_000);
    }
}
