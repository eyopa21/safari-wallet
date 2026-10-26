# Design notes

## Q1. Every wait

1. **Postgres row locks** (`select … for update`). Transfer and reversal lock both wallet rows in id order; payout acceptance locks one wallet; `finish` locks the payout row, then its wallet; freeze locks the wallet. A waiter waits until the holder commits. No transaction does I/O while holding a lock (bank calls run outside transactions), so a hold is a few statements, single-digit ms. Postgres grants row locks FIFO, so a waiter sits behind at most 2 instances × 10 pool connections. Measured: freeze/unfreeze max 465 ms with 40 clients on the same three wallets. No `lock_timeout` is set.
2. **Idempotency-Key primary key.** A concurrent duplicate blocks on the PK insert until the first request commits, then fails and replays. Bounded by one transaction.
3. **Hikari pool.** 10 connections per instance, `connection-timeout` 2000 ms, then the request fails. Tomcat has 50 threads, so up to 40 can wait on the pool.
4. **Tomcat.** Beyond 50 busy threads requests sit in the accept backlog (100), bounded only by the client's timeout.
5. **Bank HTTP.** Connect 1 s, read 5 s, only on the worker thread, never on a request thread. The worker is one scheduler thread (fixed delay 1 s) handling up to 50 submits and 50 polls per run, so a bank that hangs on every call costs up to 100 × 6 s per run. The first poll waits 5 s after submit; retries back off from 2 s doubling to 30 s.
6. **Liquibase changelog lock.** The second instance waits at startup until the first has migrated.
7. **Callback handler.** Only waits on item 1, which keeps it inside the bank's 2 s deadline.

## Q2. Database restart

Acceptance is one transaction: `wallet.reserved += amount`, a `wallet_payout` row (state ACCEPTED, status PENDING) and the idempotency record. All three exist or none do.

1. **Restart before that commit.** Nothing persisted, client gets 500, no money moved. A retry with the same `Idempotency-Key` creates the payout fresh.
2. **Restart after commit, before the worker claims the row.** Row and reservation are durable. Once Postgres is back, Hikari re-validates connections and the next worker run claims the row (`locked_until` null) and submits to the bank. Available stays reduced throughout; balance is untouched.
3. **Restart while the bank call is in flight.** The claim (`locked_until = now + 30 s`) is committed. The bank may answer ACCEPTED, but our `submitted()` transaction fails. The row stays ACCEPTED; after 30 s any instance re-claims and submits again. The bank is idempotent on `payout_id` and returns the same ACCEPTED and `bank_ref`, so it never pays twice. A bank error or timeout never marks a payout FAILED.
4. **Restart while the callback arrives.** The `bank_event` insert fails, we answer 500, the bank retries every 3 s up to 5 times. Independently the worker polls `GET /bank/payouts/{id}` 5 s after submit. Whichever signal lands first runs `finish`: lock the payout row, lock the wallet, then in one transaction capture (balance and reserved minus amount) or release (reserved minus amount) and set DONE. A later signal sees DONE and only sets `conflict` if its status differs.
5. **Restart during `finish`.** Rolled back and repeated later; the DONE check makes it idempotent. The CHECK constraint `reserved >= 0 and balance >= reserved` is the last line of defence.

## Q3. What you left out

- **Automated tests.** None. Verification was scripted: a curl pass over every endpoint and error code; 600 concurrent transfers over both instances with freeze/unfreeze interleaved (sum of balances conserved, no negative available, zero 5xx); ten simultaneous requests with one key (one transfer); five concurrent copies of one callback (handled once); killing the accepting instance after three payouts (the other finished them within 10 s). I would turn these into JUnit with Testcontainers Postgres and a WireMock bank. The hard part is the timing language ("callback can arrive before submit returns", "final within 2 minutes"): it needs a test profile with a 100 ms worker interval and an injected clock.
- **Latency targets.** p95 300 ms for 5.1 and 202 within 500 ms were observed, not measured under the spec's load: p95 was 915 ms with 40 clients on three wallets, which is lock contention by construction. I would run k6 against a realistic wallet spread.
- **Wallet creation API.** None; wallets come from a Liquibase seed.
- **Two-minute finality when the bank is unreachable.** A payout stays PENDING until the bank answers. Marking it FAILED blind could pay a payout we reported FAILED, which the spec forbids, so the two clauses conflict when the bank is down for two minutes.
- **Dead-letter handling and alerting** for exhausted retries.

## Decisions the paper left open

- Error body is `{"error": CODE, "message": …}`; unknown wallet, transfer or payout is 404.
- `Idempotency-Key` reused with a different body is 422 `IDEMPOTENCY_KEY_REUSED`. Keys are global and kept forever.
- Reversal takes no `Idempotency-Key`: the transfer row lock plus the status check turns any second attempt into 409.
- Amounts are integers in minor units; ids are a prefix plus six hex characters.
- Bank REJECTED counts as FAILED. Bank NOT_FOUND after our submit means re-submit, since the bank is idempotent on our id.
- `conflict` means a later bank signal contradicted the recorded final status.
- Setting a wallet to its current status returns the previous `changed_at`.
- The bank's `payout_id` is our own payout id, so the bank's idempotency is our double-payment guard.

## Isolation level

READ COMMITTED, the Postgres default. Correctness comes from explicit row locks: every balance change happens after `select … for update` on the wallet rows, taken in id order, and values are read after the lock, so there are no lost updates and no deadlocks. `GET /wallets/{id}` is a single-row read, so balance, reserved and available always come from one committed version. The CHECK constraint backs the invariant at the database.

## Idempotency mechanism

Table `idempotency_key(key PK, endpoint, request_hash, status_code, response_body)`. On a request: look the key up; same endpoint and same SHA-256 of the body replays the stored status and body; a different hash is 422. Otherwise the action runs and the record is inserted in the same transaction, as a real INSERT (`Persistable.isNew()`), so a concurrent duplicate blocks on the primary key, fails when the first commits, rolls back its own transfer or reservation and replays the winner's response. Failed actions store nothing, so a client may retry with the same key.
