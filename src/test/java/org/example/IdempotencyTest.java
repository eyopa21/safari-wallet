package org.example;

import org.example.api.ApiException;
import org.example.api.ErrorCode;
import org.example.api.TransferDtos.TransferRequest;
import org.example.api.TransferDtos.TransferResponse;
import org.example.service.IdempotencyService;
import org.example.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyTest extends IntegrationTest {

    @Autowired
    IdempotencyService idempotency;
    @Autowired
    TransferService transfers;

    private ResponseEntity<String> transfer(String key, TransferRequest req) {
        return idempotency.run(key, "p2p.transfer", req, HttpStatus.CREATED, TransferResponse.class,
                () -> transfers.transfer(req.fromWallet(), req.toWallet(), req.amount(), req.reference()));
    }

    @Test
    void sameKeySameBodyReplaysWithoutRunningAgain() {
        String a = newWallet(1_000);
        String b = newWallet(0);
        TransferRequest req = new TransferRequest(a, b, 100L, "rent");

        ResponseEntity<String> first = transfer("key-1-" + a, req);
        ResponseEntity<String> second = transfer("key-1-" + a, req);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(second.getBody()).isEqualTo(first.getBody());
        assertThat(balance(a)).isEqualTo(900);
        assertThat(count("select count(*) from transfer where from_wallet = ?", a)).isEqualTo(1);
    }

    @Test
    void sameKeyDifferentBodyIsRejected() {
        String a = newWallet(1_000);
        String b = newWallet(0);
        transfer("key-2-" + a, new TransferRequest(a, b, 100L, "rent"));

        assertThatThrownBy(() -> transfer("key-2-" + a, new TransferRequest(a, b, 200L, "rent")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);
        assertThat(balance(a)).isEqualTo(900);
    }

    @Test
    void concurrentRequestsWithTheSameKeyRunTheActionOnce() throws Exception {
        String a = newWallet(1_000);
        String b = newWallet(0);
        TransferRequest req = new TransferRequest(a, b, 100L, "rent");
        String key = "key-3-" + a;

        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> {
                try {
                    return transfer(key, req).getBody();
                } catch (ApiException e) {
                    assertThat(e.getCode()).isEqualTo(ErrorCode.REQUEST_IN_PROGRESS);
                    return "in-progress";
                }
            });
        }
        List<String> bodies = runConcurrently(tasks);

        assertThat(balance(a)).as("the transfer happened exactly once").isEqualTo(900);
        assertThat(count("select count(*) from transfer where from_wallet = ?", a)).isEqualTo(1);
        assertThat(bodies.stream().filter(s -> !s.equals("in-progress")).distinct()).as("every replay returned the same response").hasSize(1);
        assertLedgerReconciles();
    }

    @Test
    void failedRequestsAreNotRemembered() {
        String a = newWallet(50);
        String b = newWallet(0);
        String key = "key-4-" + a;
        assertThatThrownBy(() -> transfer(key, new TransferRequest(a, b, 100L, "too much")))
                .isInstanceOf(ApiException.class);
        // same key, now affordable: must run, not replay the failure
        ResponseEntity<String> ok = transfer(key, new TransferRequest(a, b, 40L, "fine"));
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(balance(a)).isEqualTo(10);
    }
}
