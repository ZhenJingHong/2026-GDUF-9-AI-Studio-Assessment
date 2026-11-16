package com.example.backend.budget;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findAllByMonth(LocalDate month);

    Optional<Budget> findByMonthAndCategoryIsNull(LocalDate month);

    Optional<Budget> findByMonthAndCategory(LocalDate month, String category);
}
