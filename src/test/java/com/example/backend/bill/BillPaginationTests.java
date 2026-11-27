package com.example.backend.bill;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BillPaginationTests {
    @Autowired MockMvc mvc;
    @Autowired BillRepository repository;

    @Test
    void returnsTenRowsPerPageButSummarizesAllMatchingBills() throws Exception {
        for (int amount = 1; amount <= 35; amount++) {
            insert(amount, amount % 2 == 0 ? "餐饮" : "交通", "2026-11-18 12:30:00");
        }
        insert(99, "餐饮", "2026-10-31 12:30:00");

        mvc.perform(get("/api/bill/page").param("month", "2026-11").param("page", "2"))
                .andExpect(jsonPath("$.data.items", hasSize(10)))
                .andExpect(jsonPath("$.data.items[0].amount").value(25))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.total").value(35))
                .andExpect(jsonPath("$.data.amount").value(630));

        mvc.perform(get("/api/bill/page").param("month", "2026-11").param("page", "999"))
                .andExpect(jsonPath("$.data.page").value(4))
                .andExpect(jsonPath("$.data.items", hasSize(5)));

        mvc.perform(get("/api/bill/page").param("month", "2026-11").param("category", "餐饮").param("keyword", "样例"))
                .andExpect(jsonPath("$.data.items", hasSize(10)))
                .andExpect(jsonPath("$.data.total").value(17))
                .andExpect(jsonPath("$.data.amount").value(306));

        mvc.perform(get("/api/bill").param("month", "2026-11"))
                .andExpect(jsonPath("$.data", hasSize(10)));
        mvc.perform(get("/api/stat/category").param("month", "2026-11"))
                .andExpect(jsonPath("$.data.total").value(630));
    }

    @Test
    void returnsEmptyFirstPageWithZeroTotalForNoMatches() throws Exception {
        mvc.perform(get("/api/bill/page").param("month", "2026-11").param("page", "3"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(0)))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.amount").value(0));
    }

    private void insert(int amount, String category, String date) {
        Bill bill = new Bill();
        bill.setAmount(BigDecimal.valueOf(amount));
        bill.setCategory(category);
        bill.setMerchant("分页样例");
        bill.setOccurredAt(date);
        bill.setCreatedAt(date);
        repository.save(bill);
    }
}
