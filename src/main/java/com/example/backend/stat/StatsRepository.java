package com.example.backend.stat;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class StatsRepository {
    private final JdbcClient jdbc;

    StatsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<StatsService.CategoryItem> sumByCategory(YearMonth month) {
        return jdbc.sql("""
                SELECT category, SUM(amount) AS total, COUNT(*) AS bill_count
                FROM bill
                WHERE occurred_at >= :start AND occurred_at < :end
                GROUP BY category
                ORDER BY total DESC, category ASC
                """)
                .param("start", month.atDay(1).atStartOfDay())
                .param("end", month.plusMonths(1).atDay(1).atStartOfDay())
                .query((row, index) -> new StatsService.CategoryItem(
                        row.getString("category"), row.getBigDecimal("total"), row.getLong("bill_count")))
                .list();
    }

    List<MonthlyTotal> sumByMonth(YearMonth from, YearMonth to) {
        return jdbc.sql("""
                SELECT EXTRACT(YEAR FROM occurred_at) AS bill_year,
                       EXTRACT(MONTH FROM occurred_at) AS bill_month,
                       SUM(amount) AS total
                FROM bill
                WHERE occurred_at >= :start AND occurred_at < :end
                GROUP BY EXTRACT(YEAR FROM occurred_at), EXTRACT(MONTH FROM occurred_at)
                """)
                .param("start", from.atDay(1).atStartOfDay())
                .param("end", to.plusMonths(1).atDay(1).atStartOfDay())
                .query((row, index) -> new MonthlyTotal(
                        YearMonth.of(row.getInt("bill_year"), row.getInt("bill_month")),
                        row.getBigDecimal("total")))
                .list();
    }

    RangeTotals summarizeRange(LocalDate start, LocalDate end, String category, String keyword) {
        String pattern = keyword == null ? null
                : "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return jdbc.sql("""
                SELECT SUM(amount) AS total, COUNT(*) AS bill_count, MAX(amount) AS max_amount
                FROM bill
                WHERE occurred_at >= :start AND occurred_at < :endExclusive
                  AND (:category IS NULL OR category = :category)
                  AND (:pattern IS NULL
                       OR LOWER(merchant) LIKE LOWER(:pattern) ESCAPE '!'
                       OR LOWER(note) LIKE LOWER(:pattern) ESCAPE '!')
                """)
                .param("start", start.atStartOfDay())
                .param("endExclusive", end.plusDays(1).atStartOfDay())
                .param("category", category, Types.VARCHAR)
                .param("pattern", pattern, Types.VARCHAR)
                .query((row, index) -> new RangeTotals(row.getBigDecimal("total"),
                        row.getLong("bill_count"), row.getBigDecimal("max_amount")))
                .single();
    }

    record RangeTotals(BigDecimal total, long count, BigDecimal max) {
    }

    record MonthlyTotal(YearMonth month, BigDecimal total) {
    }
}
