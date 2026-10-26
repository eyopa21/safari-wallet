package org.example.service;

import org.example.domain.Now;
import lombok.RequiredArgsConstructor;
import org.example.domain.LedgerEntry;
import org.example.domain.LedgerEntry.Source;
import org.example.domain.LedgerEntry.Type;
import org.example.domain.LedgerEntryRepository;
import org.example.domain.Wallet;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

// Every balance change is posted here as a debit leg and a credit leg of the same amount, in the
// caller's transaction, so the wallet rows and the ledger can never disagree.
// Callers must hold the row locks of the wallets they pass in.
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerEntryRepository entries;

    // wallet to wallet: transfers and reversals
    public void post(Wallet from, Wallet to, long amount, Source source, String sourceId) {
        Instant now = Now.instant();
        from.debit(amount);
        to.credit(amount);
        entries.save(LedgerEntry.wallet(from, Type.DEBIT, amount, source, sourceId, now));
        entries.save(LedgerEntry.wallet(to, Type.CREDIT, amount, source, sourceId, now));
    }

    // payout succeeded: the reserved money leaves the wallet for the bank
    public void postPayout(Wallet wallet, long amount, String payoutId) {
        Instant now = Now.instant();
        wallet.capture(amount);
        entries.save(LedgerEntry.wallet(wallet, Type.DEBIT, amount, Source.PAYOUT, payoutId, now));
        entries.save(LedgerEntry.system(LedgerEntry.BANK_ACCOUNT, Type.CREDIT, amount, Source.PAYOUT, payoutId, now));
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> recent(String accountId, int limit) {
        return entries.findByAccountIdOrderByIdDesc(accountId, PageRequest.of(0, limit));
    }
}
