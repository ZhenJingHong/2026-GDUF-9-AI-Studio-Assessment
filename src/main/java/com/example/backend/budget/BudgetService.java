package com.example.backend.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.backend.stat.StatsService;

@Service
public class BudgetService {

    static final List<String> CATEGORIES = List.of("餐饮", "交通", "购物", "娱乐", "居住", "医疗", "教育", "其他");

    private static final BigDecimal NEAR_THRESHOLD = new BigDecimal("0.80");
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final BudgetRepository repository;
    private final StatsService statsService;
    private final TransactionTemplate writeTransaction;

    BudgetService(BudgetRepository repository, StatsService statsService, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.statsService = statsService;
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    // Keep the single-instance write lock through commit and the returned snapshot.
    synchronized BudgetMonth save(YearMonth month, String category, BigDecimal amount) {
        LocalDate firstDay = month.atDay(1);
        writeTransaction.executeWithoutResult(transaction -> {
            Optional<Budget> existing = category == null
                    ? repository.findByMonthAndCategoryIsNull(firstDay)
                    : repository.findByMonthAndCategory(firstDay, category);
            Budget budget = existing.orElseGet(Budget::new);
            budget.setMonth(firstDay);
            budget.setCategory(category);
            budget.setAmount(amount);
            repository.save(budget);
        });
        return findMonth(month);
    }

    BudgetMonth findMonth(YearMonth month) {
        List<Budget> budgets = repository.findAllByMonth(month.atDay(1));
        if (budgets.isEmpty()) {
            return new BudgetMonth(month.toString(), List.of());
        }

        StatsService.CategoryStats stats = statsService.byCategory(month);
        Map<String, BigDecimal> totalsByCategory = new HashMap<>();
        for (StatsService.CategoryItem item : stats.items()) {
            totalsByCategory.put(item.category(), item.total());
        }

        List<BudgetProgress> progress = budgets.stream()
                .sorted(Comparator.comparingInt(budget -> budget.getCategory() == null
                        ? -1 : CATEGORIES.indexOf(budget.getCategory())))
                .map(budget -> progress(budget.getCategory(), budget.getAmount(), budget.getCategory() == null
                        ? stats.total() : totalsByCategory.getOrDefault(budget.getCategory(), BigDecimal.ZERO)))
                .toList();
        return new BudgetMonth(month.toString(), progress);
    }

    private static BudgetProgress progress(String category, BigDecimal amount, BigDecimal spent) {
        BigDecimal difference = amount.subtract(spent);
        BigDecimal percent = spent.multiply(ONE_HUNDRED).divide(amount, 2, RoundingMode.HALF_UP);

        Status status;
        if (difference.signum() < 0) {
            status = Status.OVER;
        } else if (difference.signum() == 0) {
            status = Status.AT_LIMIT;
        } else if (spent.compareTo(amount.multiply(NEAR_THRESHOLD)) >= 0) {
            status = Status.NEAR;
        } else {
            status = Status.NORMAL;
        }

        return new BudgetProgress(category, amount, spent, difference.max(BigDecimal.ZERO),
                difference.negate().max(BigDecimal.ZERO), percent, status);
    }

    public enum Status {
        NORMAL, NEAR, AT_LIMIT, OVER
    }

    public record BudgetProgress(String category, BigDecimal amount, BigDecimal spent, BigDecimal remaining,
            BigDecimal excess, BigDecimal percent, Status status) {
    }

    public record BudgetMonth(String month, List<BudgetProgress> budgets) {
    }
}
