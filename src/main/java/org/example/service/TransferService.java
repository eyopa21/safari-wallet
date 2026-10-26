package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.api.ApiException;
import org.example.api.ErrorCode;
import org.example.api.TransferDtos.TransferResponse;
import org.example.domain.LedgerEntry.Source;
import org.example.domain.Transfer;
import org.example.domain.TransferRepository;
import org.example.domain.Wallet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferRepository transfers;
    private final WalletService wallets;
    private final LedgerService ledger;

    @Transactional
    public TransferResponse transfer(String from, String to, long amount, String reference) {
        if (from.equals(to)) {
            throw new ApiException(ErrorCode.SAME_WALLET, "from_wallet and to_wallet are both " + from);
        }
        // both rows locked in id order so two opposite transfers can't deadlock
        Wallet first = wallets.lock(min(from, to));
        Wallet second = wallets.lock(max(from, to));
        Wallet source = first.getId().equals(from) ? first : second;
        Wallet target = source == first ? second : first;

        WalletService.requireActive(source);
        WalletService.requireActive(target);
        WalletService.requireAvailable(source, amount);

        Transfer transfer = transfers.save(new Transfer(Ids.next("T"), from, to, amount, reference, Instant.now()));
        ledger.post(source, target, amount, Source.TRANSFER, transfer.getId());
        return new TransferResponse(transfer.getId(), transfer.getStatus(), source.available(), transfer.getCreatedAt());
    }

    @Transactional
    public Transfer reverse(String transferId, String reason) {
        Transfer transfer = transfers.lockById(transferId)
                .orElseThrow(() -> new ApiException(ErrorCode.TRANSFER_NOT_FOUND, "transfer " + transferId + " does not exist"));
        if (transfer.isReversed()) {
            throw new ApiException(ErrorCode.ALREADY_REVERSED, "transfer " + transferId + " was already reversed");
        }
        if (!transfer.isCompleted()) {
            throw new ApiException(ErrorCode.NOT_REVERSIBLE, "transfer " + transferId + " is " + transfer.getStatus());
        }
        String from = transfer.getToWallet();   // money goes back the other way
        String to = transfer.getFromWallet();
        Wallet first = wallets.lock(min(from, to));
        Wallet second = wallets.lock(max(from, to));
        Wallet source = first.getId().equals(from) ? first : second;
        Wallet target = source == first ? second : first;

        WalletService.requireActive(source);
        WalletService.requireAvailable(source, transfer.getAmount());
        // target may be FROZEN: money coming back is always accepted

        transfer.reverse(Ids.next("R"), reason, Instant.now());
        ledger.post(source, target, transfer.getAmount(), Source.REVERSAL, transfer.getReversalId());
        return transfer;
    }

    private static String min(String a, String b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static String max(String a, String b) {
        return a.compareTo(b) <= 0 ? b : a;
    }
}
