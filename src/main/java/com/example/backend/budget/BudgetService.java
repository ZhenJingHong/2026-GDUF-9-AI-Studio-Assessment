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

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.backend.stat.StatsService;

@Service
public class BudgetService {

    static final List<String> CATEGORIES = List.of("餐饮", "交通", "购物", "娱乐", "居住", "医疗", "教育", "其他");

    private static final BigDecimal NEAR_THRESHOLD = new BigDecimal("0.80");
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private static final String TOTAL = "";
    private final EntityManager entityManager;
    private final BudgetRepository repository;
    private final StatsService statsService;
    private final TransactionTemplate writeTransaction;

    BudgetService(BudgetRepository repository, StatsService statsService, PlatformTransactionManager transactionManager,
            EntityManager entityManager) {
        this.entityManager = entityManager;
        this.repository = repository;
        this.statsService = statsService;
        this.writeTransaction = new TransactionTemplate(transactionManager);
    }

    // Keep the single-instance write lock through commit and the returned snapshot.
    synchronized BudgetMonth save(YearMonth month, String category, BigDecimal amount) {
        LocalDate firstDay = month.atDay(1);
        writeTransaction.executeWithoutResult(transaction -> {
            if (!isCustom(firstDay)) {
                markCustom(firstDay);
                for (Amount item : defaultAmounts()) {
                    if (!java.util.Objects.equals(item.category(), category)) {
                        repository.save(monthEntry(firstDay, item));
                    }
                }
            }
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

    synchronized BudgetMonth findMonth(YearMonth month) {
        MonthSnapshot snapshot = snapshot(month);
        return new BudgetMonth(snapshot.month(), snapshot.budgets());
    }

    synchronized Defaults defaults() {
        return new Defaults(defaultAmounts());
    }

    synchronized Defaults saveDefaults(List<Amount> amounts) {
        writeTransaction.executeWithoutResult(transaction -> {
            entityManager.createQuery("delete from BudgetDefault").executeUpdate();
            for (Amount item : amounts) {
                BudgetDefault entry = new BudgetDefault();
                entry.category = item.category() == null ? TOTAL : item.category();
                entry.amount = item.amount();
                entityManager.persist(entry);
            }
        });
        return defaults();
    }

    synchronized MonthSnapshot saveMonth(YearMonth month, List<Amount> amounts) {
        LocalDate day = month.atDay(1);
        writeTransaction.executeWithoutResult(transaction -> {
            markCustom(day);
            clearMonthRows(day);
            for (Amount item : amounts) {
                repository.save(monthEntry(day, item));
            }
        });
        return snapshot(month);
    }

    synchronized MonthSnapshot restoreMonth(YearMonth month) {
        LocalDate day = month.atDay(1);
        writeTransaction.executeWithoutResult(transaction -> {
            clearMonthRows(day);
            BudgetOverride marker = entityManager.find(BudgetOverride.class, day);
            if (marker != null) {
                entityManager.remove(marker);
            }
        });
        return snapshot(month);
    }

    private void clearMonthRows(LocalDate day) {
        repository.deleteAll(repository.findAllByMonth(day));
        repository.flush();
    }

    private void markCustom(LocalDate day) {
        if (entityManager.find(BudgetOverride.class, day) == null) {
            BudgetOverride marker = new BudgetOverride();
            marker.month = day;
            entityManager.persist(marker);
        }
    }

    private boolean isCustom(LocalDate day) {
        return entityManager.find(BudgetOverride.class, day) != null || !repository.findAllByMonth(day).isEmpty();
    }

    private static Budget monthEntry(LocalDate day, Amount item) {
        Budget entry = new Budget();
        entry.setMonth(day);
        entry.setCategory(item.category());
        entry.setAmount(item.amount());
        return entry;
    }

    private List<Amount> defaultAmounts() {
        return entityManager.createQuery("select b from BudgetDefault b", BudgetDefault.class).getResultList()
                .stream()
                .map(b -> new Amount(TOTAL.equals(b.category) ? null : b.category, b.amount))
                .sorted(Comparator.comparingInt(b -> categoryOrder(b.category())))
                .toList();
    }

    private static int categoryOrder(String category) {
        return category == null ? -1 : CATEGORIES.indexOf(category);
    }

    synchronized MonthSnapshot snapshot(YearMonth month) {
        LocalDate day = month.atDay(1);
        List<Budget> rows = repository.findAllByMonth(day);
        boolean custom = !rows.isEmpty() || entityManager.find(BudgetOverride.class, day) != null;
        Source source = custom ? Source.CUSTOM : Source.DEFAULT;
        List<Amount> budgets = custom
                ? rows.stream().map(b -> new Amount(b.getCategory(), b.getAmount())).toList()
                : defaultAmounts();
        if (budgets.isEmpty()) {
            return new MonthSnapshot(month.toString(), source, List.of());
        }

        StatsService.CategoryStats stats = statsService.byCategory(month);
        Map<String, BigDecimal> totalsByCategory = new HashMap<>();
        for (StatsService.CategoryItem item : stats.items()) {
            totalsByCategory.put(item.category(), item.total());
        }

        List<BudgetProgress> progress = budgets.stream()
                .sorted(Comparator.comparingInt(budget -> categoryOrder(budget.category())))
                .map(budget -> progress(budget.category(), budget.amount(), budget.category() == null
                        ? stats.total() : totalsByCategory.getOrDefault(budget.category(), BigDecimal.ZERO)))
                .toList();
        return new MonthSnapshot(month.toString(), source, progress);
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

    public record Amount(String category, BigDecimal amount) {}
    public record Defaults(List<Amount> budgets) {}
    public enum Source { DEFAULT, CUSTOM }
    public record MonthSnapshot(String month, Source source, List<BudgetProgress> budgets) {}

    public enum Status {
        NORMAL, NEAR, AT_LIMIT, OVER
    }

    public record BudgetProgress(String category, BigDecimal amount, BigDecimal spent, BigDecimal remaining,
            BigDecimal excess, BigDecimal percent, Status status) {
    }

    public record BudgetMonth(String month, List<BudgetProgress> budgets) {
    }
}
