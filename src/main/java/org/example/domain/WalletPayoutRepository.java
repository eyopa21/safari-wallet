package org.example.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WalletPayoutRepository extends JpaRepository<WalletPayout, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from WalletPayout p where p.id = :id")
    Optional<WalletPayout> lockById(String id);

    // due work nobody else is holding; skip locked so two instances never pick the same row
    @Query(value = """
            select * from wallet_payout
            where state = :state and next_attempt_at <= now()
              and (locked_until is null or locked_until < now())
            order by next_attempt_at
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<WalletPayout> findDueForUpdate(String state, int limit);
}
