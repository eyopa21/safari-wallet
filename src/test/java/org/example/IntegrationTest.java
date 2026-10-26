package org.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

// One Postgres for the whole test run; every test class gets the real schema from Liquibase.
@SpringBootTest(classes = Main.class, properties = {
        "spring.docker.compose.enabled=false",
        "spring.liquibase.contexts=test",      // no seeded wallets, tests make their own
        "bank.base-url=http://localhost:1",    // nothing listens here: the worker's bank calls fail fast
        "bank.worker-enabled=false",           // each test drives the service directly; PayoutFlowTest turns the worker on
        "bank.worker-interval=200ms",
        "bank.poll-interval=200ms",
        "bank.retry-interval=200ms",
        "bank.claim-time=2s"
})
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected JdbcTemplate jdbc;

    protected String newWallet(long balance) {
        String id = "T" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("insert into wallet (id, status, balance, reserved) values (?, 'ACTIVE', ?, 0)", id, balance);
        if (balance > 0) {
            jdbc.update("insert into ledger_entry (account_id, entry_type, amount, balance_after, source_type, source_id) values (?, 'CREDIT', ?, ?, 'OPENING', ?)", id, balance, balance, id);
            jdbc.update("insert into ledger_entry (account_id, entry_type, amount, balance_after, source_type, source_id) values ('SYS:OPENING', 'DEBIT', ?, null, 'OPENING', ?)", balance, id);
        }
        return id;
    }

    protected long balance(String wallet) {
        return jdbc.queryForObject("select balance from wallet where id = ?", Long.class, wallet);
    }

    protected long reserved(String wallet) {
        return jdbc.queryForObject("select reserved from wallet where id = ?", Long.class, wallet);
    }

    protected long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    // the two ledger invariants from DESIGN.md
    protected void assertLedgerReconciles() {
        long unbalanced = count("""
                select count(*) from (
                  select source_type, source_id from ledger_entry group by 1, 2
                  having sum(case when entry_type = 'DEBIT' then amount else -amount end) <> 0) x""");
        assertThat(unbalanced).as("postings that do not sum to zero").isZero();
        long unexplained = count("""
                select count(*) from (
                  select w.id from wallet w left join ledger_entry l on l.account_id = w.id
                  group by w.id, w.balance
                  having w.balance <> coalesce(sum(case when l.entry_type = 'CREDIT' then l.amount else -l.amount end), 0)) x""");
        assertThat(unexplained).as("wallets whose balance the ledger does not explain").isZero();
    }

    // runs all tasks at the same moment and waits for them
    protected <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = tasks.stream().map(t -> pool.submit(() -> {
            go.await();
            return t.call();
        })).toList();
        go.countDown();
        List<T> results = new java.util.ArrayList<>();
        for (Future<T> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();
        return results;
    }
}
