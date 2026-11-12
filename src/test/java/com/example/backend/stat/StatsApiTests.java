package com.example.backend.stat;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
    void rangeDefaultsToSumAndIncludesBothDatesAcrossYears() throws Exception {
        insertBill("餐饮", "100.00", "2026-12-30T23:59:59.999999");
        insertBill("餐饮", "10.10", "2026-12-31T00:00:00");
        insertBill("交通", "3.20", "2027-01-02T23:59:59.999999");
        insertBill("购物", "200.00", "2027-01-03T00:00:00");

        mvc.perform(dateRange("2026-12-31", "2027-01-02"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"code": 0, "message": "ok", "data": {
                          "start": "2026-12-31", "end": "2027-01-02", "category": null,
                          "keyword": null, "aggregate": "sum", "value": 13.30
                        }}
                        """, JsonCompareMode.STRICT));
    }

    @Test
    void rangeSupportsEveryAggregateAndAppliesBothFilters() throws Exception {
        insertBill("餐饮", "12.00", "2026-11-05T00:00:00", "Cafe_100%!", null);
        insertBill("餐饮", "22.01", "2026-11-05T23:59:59.999999", null, "at cafe_100%! today");
        insertBill("餐饮", "90.00", "2026-11-05T12:00:00", "CafeA100XYZ!", null);
        insertBill("购物", "80.00", "2026-11-05T12:00:00", "Cafe_100%!", null);
        insertBill("餐饮", "70.00", "2026-11-06T00:00:00", "Cafe_100%!", null);

        expectFilteredAggregate("sum", 34.01);
        expectFilteredAggregate("count", 2);
        expectFilteredAggregate("max", 22.01);
        expectFilteredAggregate("avg", 17.01);
    }

    @Test
    void emptyRangeHasZeroSumAndCountAndNullMaxAndAverage() throws Exception {
        for (String aggregate : new String[] {"sum", "count", "max", "avg"}) {
            ResultActions result = mvc.perform(dateRange("2026-11-05", "2026-11-18")
                    .param("aggregate", aggregate)).andExpect(status().isOk());
            if (aggregate.equals("sum") || aggregate.equals("count")) {
                result.andExpect(jsonPath("$.data.value").value(0));
            } else {
                result.andExpect(jsonPath("$.data.value").value(nullValue()));
            }
        }
    }

    @Test
    void rangeRejectsMissingInvalidOrReversedDatesAndUnknownOptions() throws Exception {
        expectBadRequest(mvc.perform(get("/api/stat/range").param("end", "2026-11-18")),
                "缺少参数 start");
        expectBadRequest(mvc.perform(get("/api/stat/range").param("start", "2026-11-05")),
                "缺少参数 end");
        expectBadRequest(mvc.perform(dateRange("2026-02-29", "2026-11-18")),
                "start 必须为有效的 yyyy-MM-dd 日期");
        expectBadRequest(mvc.perform(dateRange("2026-11-05", "2026-11-8")),
                "end 必须为有效的 yyyy-MM-dd 日期");
        expectBadRequest(mvc.perform(dateRange("2026-11-18", "2026-11-05")),
                "start 不能晚于 end");
        expectBadRequest(mvc.perform(dateRange("2026-11-05", "2026-11-18").param("aggregate", "median")),
                "aggregate 必须是 sum、count、max 或 avg");
        expectBadRequest(mvc.perform(dateRange("2026-11-05", "2026-11-18").param("category", "咖啡")),
                40002, "category 不在分类枚举内");
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
        insertBill(category, amount, occurredAt, null, null);
    }

    private void insertBill(String category, String amount, String occurredAt, String merchant, String note) {
        jdbc.sql("INSERT INTO bill (category, amount, occurred_at, merchant, note) VALUES (?, ?, ?, ?, ?)")
                .param(category)
                .param(new BigDecimal(amount))
                .param(LocalDateTime.parse(occurredAt))
                .param(merchant)
                .param(note)
                .update();
    }

    private MockHttpServletRequestBuilder dateRange(String start, String end) {
        return get("/api/stat/range").param("start", start).param("end", end);
    }

    private void expectFilteredAggregate(String aggregate, Number value) throws Exception {
        mvc.perform(dateRange("2026-11-05", "2026-11-05").param("aggregate", aggregate)
                        .param("category", "餐饮").param("keyword", " CAFE_100%! "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("餐饮"))
                .andExpect(jsonPath("$.data.keyword").value("CAFE_100%!"))
                .andExpect(jsonPath("$.data.aggregate").value(aggregate))
                .andExpect(jsonPath("$.data.value").value(value));
    }

    private void expectBadRequest(ResultActions result, String message) throws Exception {
        expectBadRequest(result, 40001, message);
    }

    private void expectBadRequest(ResultActions result, int code, String message) throws Exception {
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
