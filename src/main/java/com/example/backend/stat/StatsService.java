package com.example.backend.stat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class StatsService {
    private final StatsRepository repository;

    StatsService(StatsRepository repository) {
        this.repository = repository;
    }

    public CategoryStats byCategory(YearMonth month) {
        List<CategoryItem> items = repository.sumByCategory(month);
        BigDecimal total = BigDecimal.ZERO;
        for (CategoryItem item : items) {
            total = total.add(item.total());
        }
        return new CategoryStats(month.toString(), total, items);
    }

    public MonthlyTrend trend(YearMonth from, YearMonth to) {
        Map<YearMonth, BigDecimal> totalsByMonth = new HashMap<>();
        for (StatsRepository.MonthlyTotal result : repository.sumByMonth(from, to)) {
            totalsByMonth.put(result.month(), result.total());
        }

        List<String> months = new ArrayList<>();
        List<BigDecimal> totals = new ArrayList<>();
        for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
            months.add(month.toString());
            totals.add(totalsByMonth.getOrDefault(month, BigDecimal.ZERO));
        }
        return new MonthlyTrend(months, totals);
    }

    public RangeStat range(LocalDate start, LocalDate end, String category, String keyword,
            RangeAggregate aggregate) {
        StatsRepository.RangeTotals totals = repository.summarizeRange(start, end, category, keyword);
        Number value = switch (aggregate) {
            case SUM -> totals.count() == 0 ? BigDecimal.ZERO : totals.total();
            case COUNT -> totals.count();
            case MAX -> totals.max();
            case AVG -> totals.count() == 0 ? null : totals.total()
                    .divide(BigDecimal.valueOf(totals.count()), 2, RoundingMode.HALF_UP);
        };
        return new RangeStat(start.toString(), end.toString(), category, keyword,
                aggregate.name().toLowerCase(Locale.ROOT), value);
    }

    public enum RangeAggregate {
        SUM, COUNT, MAX, AVG
    }

    public record RangeStat(String start, String end, String category, String keyword, String aggregate,
            Number value) {
    }

    public record CategoryItem(String category, BigDecimal total, long count) {
    }

    public record CategoryStats(String month, BigDecimal total, List<CategoryItem> items) {
    }

    public record MonthlyTrend(List<String> months, List<BigDecimal> totals) {
    }
}
