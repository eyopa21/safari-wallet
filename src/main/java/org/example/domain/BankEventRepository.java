package org.example.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BankEventRepository extends JpaRepository<BankEvent, String> {
}
