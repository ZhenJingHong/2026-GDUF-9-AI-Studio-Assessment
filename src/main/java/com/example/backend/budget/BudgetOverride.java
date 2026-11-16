package com.example.backend.budget;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "budget_month_override")
class BudgetOverride {
    @Id
    @Column(name = "budget_month")
    LocalDate month;

    protected BudgetOverride() {}
}
