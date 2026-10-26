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
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReversalTest extends IntegrationTest {

    @Autowired
    TransferService transfers;
    @Autowired
    WalletService wallets;

    @Test
    void concurrentReversalsSucceedExactlyOnce() throws Exception {
        String a = newWallet(1_000);
        String b = newWallet(0);
        String transferId = transfers.transfer(a, b, 400, "x").transferId();

        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> {
                try {
                    transfers.reverse(transferId, "dispute");
                    return "reversed";
                } catch (ApiException e) {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.ALREADY_REVERSED);
                    return "already";
                }
            });
        }
        List<String> results = runConcurrently(tasks);

        assertThat(results).containsOnlyOnce("reversed");
        assertThat(balance(a)).isEqualTo(1_000);
        assertThat(balance(b)).isZero();
        assertThat(count("select count(*) from ledger_entry where source_type = 'REVERSAL' and account_id in (?, ?)", a, b)).isEqualTo(2);
        assertLedgerReconciles();
    }

    @Test
    void moneyReturningToAFrozenWalletIsAccepted() {
        String a = newWallet(1_000);
        String b = newWallet(0);
        String transferId = transfers.transfer(a, b, 300, "x").transferId();
        wallets.changeStatus(a, WalletStatus.FROZEN, "fraud review");

        transfers.reverse(transferId, "refund");

        assertThat(balance(a)).isEqualTo(1_000);
        assertThat(balance(b)).isZero();
    }

    @Test
    void reversalNeedsAnActiveDebitedWalletWithFunds() {
        String a = newWallet(1_000);
        String b = newWallet(0);
        String c = newWallet(0);
        String transferId = transfers.transfer(a, b, 300, "x").transferId();
        transfers.transfer(b, c, 300, "spent already");

        assertThatThrownBy(() -> transfers.reverse(transferId, "r"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);

        wallets.changeStatus(b, WalletStatus.FROZEN, "x");
        assertThatThrownBy(() -> transfers.reverse(transferId, "r"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.WALLET_NOT_ACTIVE);
    }
}
