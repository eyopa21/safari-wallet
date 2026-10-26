# Design

Two copies of this service run against one Postgres. They share nothing else.
The rule for every write path is the same: lock the rows, check, change, write
the ledger, commit. No HTTP call ever happens inside a transaction.

## Tables

- `wallet` - id, status, balance, reserved. available = balance - reserved, computed on read.
  A check constraint keeps reserved >= 0 and balance >= reserved, so available can't go negative.
- `transfer` - one row per transfer, COMPLETED or REVERSED.
- `wallet_payout` - one row per payout. Public status (PENDING / SUCCEEDED / FAILED)
  plus an internal state (ACCEPTED / SUBMITTED / DONE) and retry fields.
- `bank_event` - one row per callback event_id. The primary key is the dedup.
- `idempotency_key` - key, request hash, stored response.
- `ledger_entry` - double entry legs.

Liquibase owns the schema. Hibernate only validates it.

## How each write works

**Transfer.** One transaction. Lock both wallets (lower id first, so A->B and B->A
can't deadlock), check active and funds on the locked rows, debit, credit, write
two ledger legs, commit. Nobody can see the debit without the credit.

**Reversal.** Lock the transfer row first, then the wallets in id order. Second
caller waits, sees REVERSED, gets 409. The debited side must be active with
funds. The credited side may be frozen.

**Freeze.** Takes the same wallet row lock a debit takes. It waits for debits
already running, and every debit after it sees FROZEN. Works across both
instances with nothing extra. Same status again is a no-op with 200.

**Payout accept.** Lock wallet, check, reserved += amount, insert row, commit, 202.
No bank call here.

**Payout worker.** Runs on both instances every 500 ms. Claims due rows with
`FOR UPDATE SKIP LOCKED` and a 30 s lease, then hands them to a pool of 8 bank
threads. ACCEPTED rows get submitted; SUBMITTED rows get polled if no callback
came. If the instance dies, the lease runs out and the other instance picks the
row up.

**Callback.** Insert event_id into `bank_event`. Duplicate -> 200, nothing else.
First copy runs `finish`.

**finish** is the only place reserved money leaves or returns. Lock payout, lock
wallet. Already DONE -> do nothing, set `conflict` if the new answer differs.
SUCCEEDED -> balance -= amount, reserved -= amount, ledger legs. FAILED ->
reserved -= amount.

Lock order everywhere: payout or transfer row first, then wallets by id.

## Ledger

Every balance change writes a debit leg and a credit leg of the same amount in the
same transaction. Transfers go wallet to wallet. A payout debits the wallet and
credits `SYS:BANK`. Opening balances come from `SYS:OPENING`. Reservations are not
ledger entries, they don't change balance.

Two checks that must always return nothing:

```sql
select source_type, source_id from ledger_entry group by 1,2
having sum(case when entry_type='DEBIT' then amount else -amount end) <> 0;

select w.id from wallet w left join ledger_entry l on l.account_id = w.id
group by w.id, w.balance
having w.balance <> coalesce(sum(case when l.entry_type='CREDIT' then l.amount else -l.amount end),0);
```

## Idempotency

The response is saved in the same transaction as the action. Same key and body
-> stored response. Same key, other body -> 409. Two at once with the same key ->
the loser hits the primary key, rolls back fully, then replays the winner.
Failed requests are not saved, so a corrected retry works.

## Threads and connections

| | | |
|---|---|---|
| Tomcat threads | 24 | never wait on the bank |
| bank threads | 8 | the only ones that call the bank |
| Hikari pool | 32 | 24 + 8, no thread ever waits for a connection |
| bank read timeout | 8 s | above the bank's 6 s slow path |
| payout lease | 30 s | longer than connect + read timeout |

## Bank rules

- Never pay twice: one payout_id per payout, the bank is idempotent on it.
- Never pay something I called FAILED: FAILED only comes from the bank's own answer.
  A timeout is retried, never failed.
- Callbacks twice, out of order, or before my submit returns: event dedup plus the
  payout row lock. First answer wins, a different later one sets `conflict`.

## Bank mock

The real bank isn't available, so I wrote `../external-bank` to behave like its
spec: accept or reject on submit, 1 to 3 s status latency, callbacks after 1 to
10 s to either instance, 30% duplicated, retried when no 2xx in 2 s, about 5%
failures. All of it is configurable so a scenario can force duplicates or make a
target unreachable. I used it to run the full spec, 60 parallel transfers with a
freeze in the middle, and a payout accepted on an instance I then killed.

## Decisions

1. Row locks, not optimistic versioning. Contention is the whole problem.
2. Freeze is a locked update. No flags, no cache.
3. Reserve on accept, capture on success, release on failure.
4. Worker on every instance with SKIP LOCKED and a lease. No leader.
5. Bank calls only from the bounded pool.
6. Timeout is never FAILED.
7. Callback dedup by primary key on event_id.
8. Idempotency saved with the action, in one transaction.
9. Double entry ledger with system accounts.
10. Amounts are whole numbers in BIGINT.
11. Wallets are seeded by migration. There is no create endpoint in the spec.
12. `conflict` = a later bank answer disagreed with the stored final status.
13. Error body is `{"error": CODE, "message": text}`.
14. Timestamps are cut to milliseconds at the source (`Now.instant()`). Java gives
    nanos and Postgres keeps micros, so without this a PUT and a later GET would show
    different digits for the same moment. Millis also matches the spec examples.

## Running

```
mvn spring-boot:run                                              # db + instance 1
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081   # instance 2
cd ../external-bank && mvn spring-boot:run                       # bank mock on 9090
mvn test                                                         # needs Docker
```

Testcontainers is pinned to 1.21.4 in the pom: the version Boot 3.5.6 manages uses
Docker API 1.32, which Docker Engine 29 refuses. If the default Docker context is not
the running daemon, set `DOCKER_HOST` before `mvn test`.

Swagger is at `/swagger-ui.html`. Tests use Testcontainers and cover: parallel
transfers keep the total, freeze blocks later debits, same key runs once, reversal
runs once, three copies of a callback finish once, a lease expires and the row is
claimed again, and the payout flow against a stubbed bank.

## Not done

- Disbursement
- Statement
