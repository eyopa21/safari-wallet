package org.example;

import org.example.domain.WalletPayout;
import org.example.service.BankClient;
import org.example.service.BankClient.SubmitResult;
import org.example.service.WalletPayoutService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

// the worker runs for real; only the HTTP client to the bank is stubbed.
// the context is dropped afterwards so this worker cannot touch rows of other test classes
@TestPropertySource(properties = "bank.worker-enabled=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PayoutFlowTest extends IntegrationTest {

    @Autowired
    WalletPayoutService payouts;
    @MockitoBean
    BankClient bank;

    @Test
    void acceptedThenSucceededCapturesTheReservation() {
        String w = newWallet(10_000);
        WalletPayout p = payouts.accept(w, "ACC-001", 3_000);
        when(bank.submit(eq(p.getId()), anyString(), anyLong())).thenReturn(new SubmitResult("ACCEPTED", "BR-OK", null));
        when(bank.status(p.getId())).thenReturn("SUCCEEDED");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(payouts.get(p.getId()).getStatus().name()).isEqualTo("SUCCEEDED"));

        assertThat(payouts.get(p.getId()).getBankRef()).isEqualTo("BR-OK");
        assertThat(balance(w)).isEqualTo(7_000);
        assertThat(reserved(w)).isZero();
        assertLedgerReconciles();
    }

    @Test
    void rejectedBySubmitReleasesTheReservation() {
        String w = newWallet(10_000);
        WalletPayout p = payouts.accept(w, "XYZ", 3_000);
        when(bank.submit(eq(p.getId()), anyString(), anyLong()))
                .thenReturn(new SubmitResult("REJECTED", null, "BENEFICIARY_ACCOUNT_INVALID"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(payouts.get(p.getId()).getStatus().name()).isEqualTo("FAILED"));

        assertThat(balance(w)).isEqualTo(10_000);
        assertThat(reserved(w)).isZero();
    }

    @Test
    void aSubmitTimeoutIsRetriedNeverFailed() {
        String w = newWallet(10_000);
        WalletPayout p = payouts.accept(w, "ACC-001", 3_000);
        when(bank.submit(eq(p.getId()), anyString(), anyLong()))
                .thenThrow(new ResourceAccessException("read timed out"))
                .thenThrow(new ResourceAccessException("read timed out"))
                .thenReturn(new SubmitResult("ACCEPTED", "BR-LATE", null));
        when(bank.status(p.getId())).thenReturn("IN_PROGRESS", "SUCCEEDED");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(payouts.get(p.getId()).getStatus().name()).isEqualTo("SUCCEEDED"));

        assertThat(balance(w)).isEqualTo(7_000);
        assertThat(reserved(w)).isZero();
    }
}
