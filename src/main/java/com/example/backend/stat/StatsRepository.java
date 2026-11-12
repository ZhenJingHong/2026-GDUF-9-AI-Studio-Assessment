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
        return queryBetween("""
                SELECT category, SUM(amount) AS total, COUNT(*) AS bill_count
                FROM bill
                WHERE occurred_at BETWEEN :start AND :end
                GROUP BY category
                ORDER BY total DESC, category ASC
                """, month.atDay(1), month.atEndOfMonth())
                .query((row, index) -> new StatsService.CategoryItem(
                        row.getString("category"), row.getBigDecimal("total"), row.getLong("bill_count")))
                .list();
    }

    List<MonthlyTotal> sumByMonth(YearMonth from, YearMonth to) {
        return queryBetween("""
                SELECT SUBSTRING(occurred_at, 1, 7) AS bill_month, SUM(amount) AS total
                FROM bill
                WHERE occurred_at BETWEEN :start AND :end
                GROUP BY SUBSTRING(occurred_at, 1, 7)
                """, from.atDay(1), to.atEndOfMonth())
                .query((row, index) -> new MonthlyTotal(
                        YearMonth.parse(row.getString("bill_month")),
                        row.getBigDecimal("total")))
                .list();
    }

    RangeTotals summarizeRange(LocalDate start, LocalDate end, String category, String keyword) {
        String pattern = keyword == null ? null
                : "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return queryBetween("""
                SELECT SUM(amount) AS total, COUNT(*) AS bill_count, MAX(amount) AS max_amount
                FROM bill
                WHERE occurred_at BETWEEN :start AND :end
                  AND (:category IS NULL OR category = :category)
                  AND (:pattern IS NULL
                       OR LOWER(merchant) LIKE LOWER(:pattern) ESCAPE '!'
                       OR LOWER(note) LIKE LOWER(:pattern) ESCAPE '!')
                """, start, end)
                .param("category", category, Types.VARCHAR)
                .param("pattern", pattern, Types.VARCHAR)
                .query((row, index) -> new RangeTotals(row.getBigDecimal("total"),
                        row.getLong("bill_count"), row.getBigDecimal("max_amount")))
                .single();
    }

    private JdbcClient.StatementSpec queryBetween(String sql, LocalDate start, LocalDate end) {
        // Bill stores occurred_at as a fixed-width string with second precision.
        return jdbc.sql(sql)
                .param("start", start + " 00:00:00")
                .param("end", end + " 23:59:59");
    }

    record RangeTotals(BigDecimal total, long count, BigDecimal max) {
    }

    record MonthlyTotal(YearMonth month, BigDecimal total) {
    }
}
