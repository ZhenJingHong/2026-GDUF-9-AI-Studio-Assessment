package com.example.backend.budget;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "budget_default")
class BudgetDefault {
    @Id
    String category;
    @Column(nullable = false, precision = 12, scale = 2)
    BigDecimal amount;
    protected BudgetDefault() {}
}
