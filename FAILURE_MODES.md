# Failure modes

For each API I listed how it could lose money, make money, pay twice, or hang,
and what I did about it. Marks are honest: some are `limited` or `not handled`.

Three rules cover most lines: money moves in one transaction, wallet rows are
locked before anything is checked, and no HTTP call happens inside a transaction.

## All endpoints

- Client retries a POST that already went through -> Idempotency-Key saved in the same transaction; a race loser rolls back and replays the winner. `prevented`
- Same key, different body, old answer replayed -> body is hashed, mismatch is 409. `prevented`
- Error shows a stack trace or SQL -> one handler, fixed messages, details in the log. `prevented`
- Postgres gone, requests hang -> Hikari waits 2 s max, then 503. `prevented`
- A request holds a wallet lock while waiting on the bank -> the bank is only called from the worker, outside any transaction. `prevented`
- One very hot wallet ties up all request threads -> transactions are tiny but there is no lock timeout. `limited`
- Worker throws once and stops -> each run is try/catch. `prevented`
- Balance overflows long -> amounts are capped, balance is not. `not handled`

## Transfers

- Debit commits, credit doesn't -> one transaction. `prevented`
- Two transfers pass the funds check on the same balance -> check runs on the locked row. `prevented`
- A->B and B->A deadlock -> lower id locked first. `prevented`
- A bug makes available negative -> check constraint fails the commit. `prevented`
- Target missing, source still debited -> both locked and checked before any change. `prevented`
- Wallet frozen a moment ago but still debited -> freeze takes the same lock. `prevented`
- Reader on the other instance sees half -> single transaction, read committed. `prevented`
- Generated id collides -> primary key fails the insert, no retry. `limited`

## Reversal

- Reversed twice -> transfer row locked, second sees REVERSED, 409. `prevented`
- Two reversals at once -> same lock, serialised. `prevented`
- Receiver already spent it -> funds checked under lock, 422. `prevented`
- Sender frozen so the refund is stuck -> money coming back into FROZEN is accepted. `prevented`
- Deadlock with a transfer -> same lock order. `prevented`
- Client retries the reversal -> gets 409, nothing moves twice. `limited`

## Wallet get and freeze

- Snapshot mixes two moments -> one row, one read. `prevented`
- Freeze returns 200 but an earlier debit commits after -> freeze holds the row lock until those finish. `prevented`
- Freeze only known to one instance -> status is in the row. `prevented`
- Constant debits starve the freeze past 2 s -> locks are roughly FIFO and short, but no priority. `limited`
- Freezing again rewrites changed_at -> no-op. `prevented`
- Freeze strands an earlier payout's reservation -> finish ignores wallet status. `prevented`
- Freeze lets a new payout reserve -> accept checks ACTIVE under lock. `prevented`

## Payouts and callbacks

- Reserved but no row, or the reverse -> one transaction. `prevented`
- 202 waits on the bank -> accept only touches the database. `prevented`
- Submit times out, bank paid, I mark FAILED -> timeouts never finalise; retry, bank is idempotent. `prevented`
- Submitted twice, paid twice -> one payout_id, bank returns the stored result. `prevented`
- Accepting instance dies -> SKIP LOCKED plus 30 s lease, the other instance takes it. `prevented`
- Both instances submit the same row -> SKIP LOCKED; and the bank is idempotent anyway. `prevented`
- Callback arrives 2 or 3 times at once -> event_id primary key, duplicates get 200 and do nothing. `prevented`
- Callback and poll both finalise -> finish locks the row, DONE is a no-op. `prevented`
- Callback says one thing, poll says another -> first wins, `conflict` set, logged. `limited`
- Callback before my submit returns -> finish by payout_id, the late update is skipped. `prevented`
- Callback for an unknown payout -> 200 and a log line. `prevented`
- Callback slower than the bank's 2 s -> two short transactions, no outbound calls. `prevented`
- Capture or release more than reserved -> guarded in code and by the constraint. `prevented`
- Bank down for minutes -> keep retrying with backoff, never guess FAILED; 2 minutes not met without the bank. `limited`
- Bank says NOT_FOUND for something it accepted -> resubmit, idempotent. `limited`
- Lease expires mid call, other instance repeats it -> harmless, same result written. `limited`
- Client retries accept -> Idempotency-Key replay. `prevented`
- I say FAILED and the bank pays -> FAILED only comes from the bank. `prevented`

## Bank mock

- Pays the same payout_id twice -> unique id, repeat returns stored outcome. `prevented`
- Callback before the outcome is committed -> scheduled after commit. `prevented`
- Restart forgets pending callbacks -> in memory on purpose; my status poll still settles it. `not handled`
- Settle outside a transaction, status lost -> explicit TransactionTemplate. I hit this one. `prevented`

## Batches

Not built. `not handled`
