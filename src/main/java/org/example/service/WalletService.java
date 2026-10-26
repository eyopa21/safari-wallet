package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.api.ApiException;
import org.example.api.ErrorCode;
import org.example.domain.Wallet;
import org.example.domain.WalletRepository;
import org.example.domain.WalletStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository wallets;

    @Transactional(readOnly = true)
    public Wallet get(String id) {
        return wallets.findById(id).orElseThrow(() -> notFound(id));
    }

    // takes the row lock, so it waits for in-flight debits and nothing after it can debit a frozen wallet
    @Transactional
    public Wallet changeStatus(String id, WalletStatus status, String reason) {
        Wallet wallet = lock(id);
        wallet.changeStatus(status, reason, Instant.now());
        return wallet;
    }

    Wallet lock(String id) {
        return wallets.lockById(id).orElseThrow(() -> notFound(id));
    }

    static void requireActive(Wallet wallet) {
        if (!wallet.isActive()) {
            throw new ApiException(ErrorCode.WALLET_NOT_ACTIVE, wallet.getId() + " is " + wallet.getStatus());
        }
    }

    static void requireAvailable(Wallet wallet, long amount) {
        if (wallet.available() < amount) {
            throw new ApiException(ErrorCode.INSUFFICIENT_FUNDS,
                    wallet.getId() + " available " + wallet.available() + ", requested " + amount);
        }
    }

    private static ApiException notFound(String id) {
        return new ApiException(ErrorCode.WALLET_NOT_FOUND, "wallet " + id + " does not exist");
    }
}
