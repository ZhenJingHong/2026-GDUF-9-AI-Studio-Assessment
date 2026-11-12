package com.example.backend.stats;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StatsApiTests {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void categorySumsAndCountsBillsInTheSelectedMonth() throws Exception {
        insertBill("餐饮", "25.50", "2026-11-01T00:00:00");
        insertBill("餐饮", "14.50", "2026-11-30T23:59:59.999999");
        insertBill("交通", "12.50", "2026-11-08T12:00:00");
        insertBill("购物", "30.00", "2026-11-12T12:00:00");
        insertBill("餐饮", "100.00", "2026-10-31T23:59:59");
        insertBill("餐饮", "200.00", "2026-12-01T00:00:00");

        mvc.perform(get("/api/stat/category").param("month", "2026-11"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"code": 0, "message": "ok", "data": {
                          "month": "2026-11", "total": 82.50, "items": [
                            {"category": "餐饮", "total": 40.00, "count": 2},
                            {"category": "购物", "total": 30.00, "count": 1},
                            {"category": "交通", "total": 12.50, "count": 1}
                          ]
                        }}
                        """, JsonCompareMode.STRICT));

        insertBill("餐饮", "0.25", "2026-11-13T12:00:00");
        mvc.perform(get("/api/stat/category").param("month", "2026-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(82.75))
                .andExpect(jsonPath("$.data.items[0].count").value(3));
    }

    @Test
    void categoryReturnsAnEmptyListAndZeroWhenThereAreNoBills() throws Exception {
        mvc.perform(get("/api/stat/category").param("month", "2026-10"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"code": 0, "message": "ok", "data": {"month": "2026-10", "total": 0, "items": []}}
                        """, JsonCompareMode.STRICT));
    }

    @Test
    void trendIncludesBothEndpointsAndFillsMissingMonthsAcrossYears() throws Exception {
        insertBill("餐饮", "0.10", "2026-11-01T00:00:00");
        insertBill("交通", "0.20", "2026-11-15T12:00:00");
        insertBill("购物", "37.25", "2027-01-31T23:59:59");
        insertBill("购物", "100.00", "2027-02-01T00:00:00");

        mvc.perform(get("/api/stat/trend").param("from", "2026-11").param("to", "2027-01"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"code": 0, "message": "ok", "data": {
                          "months": ["2026-11", "2026-12", "2027-01"],
                          "totals": [0.30, 0, 37.25]
                        }}
                        """, JsonCompareMode.STRICT));
    }

    @Test
    void trendAllowsASingleEmptyMonth() throws Exception {
        mvc.perform(get("/api/stat/trend").param("from", "2026-11").param("to", "2026-11"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"code": 0, "message": "ok", "data": {"months": ["2026-11"], "totals": [0]}}
                        """, JsonCompareMode.STRICT));
    }

    @Test
    void invalidQueriesReturnTheDocumentedErrorEnvelope() throws Exception {
        expectBadRequest(mvc.perform(get("/api/stat/category")), "缺少参数 month");
        expectBadRequest(mvc.perform(get("/api/stat/category").param("month", "2026-13")),
                "month 必须为有效的 yyyy-MM 月份");
        expectBadRequest(mvc.perform(get("/api/stat/category").param("month", "")),
                "month 必须为有效的 yyyy-MM 月份");
        expectBadRequest(mvc.perform(get("/api/stat/trend").param("from", "2026-11")), "缺少参数 to");
        expectBadRequest(mvc.perform(get("/api/stat/trend").param("from", "2026-9").param("to", "2026-11")),
                "from 必须为有效的 yyyy-MM 月份");
        expectBadRequest(mvc.perform(get("/api/stat/trend").param("from", "2026-11").param("to", "2026-09")),
                "from 不能晚于 to");
    }

    private void insertBill(String category, String amount, String occurredAt) {
        jdbc.sql("INSERT INTO bill (category, amount, occurred_at) VALUES (?, ?, ?)")
                .param(category)
                .param(new BigDecimal(amount))
                .param(LocalDateTime.parse(occurredAt))
                .update();
    }

    private void expectBadRequest(ResultActions result, String message) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
