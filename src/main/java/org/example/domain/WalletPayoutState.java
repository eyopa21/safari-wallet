package org.example.domain;

// where we are with the bank
public enum WalletPayoutState {
    ACCEPTED,   // reserved, not yet handed to the bank
    SUBMITTED,  // bank has it, waiting for callback or status poll
    DONE
}
