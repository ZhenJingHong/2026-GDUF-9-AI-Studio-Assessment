package com.example.backend.bill;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class BillDeletionTests {
    private static final String SNAPSHOT = """
            {"amount":60,"category":"餐饮","occurredAt":"2026-11-18 12:30:00","timePrecision":"EXACT","createdAt":"2026-11-18 12:31:00"}
            """;

    @Autowired MockMvc mvc;
    @Autowired BillRepository repository;

    @Test
    void confirmsOnlyTheChosenBillWhenTwoAmountsMatch() throws Exception {
        Bill chosen = insert();
        Bill other = insert();

        mvc.perform(delete("/api/bill/{id}/confirmed", chosen.getId())
                .contentType("application/json").content(SNAPSHOT))
                .andExpect(jsonPath("$.code").value(0));
        assertTrue(repository.findById(chosen.getId()).isEmpty());
        assertTrue(repository.existsById(other.getId()));

        mvc.perform(delete("/api/bill/{id}/confirmed", chosen.getId())
                .contentType("application/json").content(SNAPSHOT))
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    void rejectsAStaleConfirmationAfterAmountChanges() throws Exception {
        Bill chosen = insert();

        mvc.perform(put("/api/bill/{id}/amount", chosen.getId())
                .contentType("application/json").content("{\"amount\":40}"))
                .andExpect(jsonPath("$.data.amount").value(40));
        mvc.perform(delete("/api/bill/{id}/confirmed", chosen.getId())
                .contentType("application/json").content(SNAPSHOT))
                .andExpect(jsonPath("$.code").value(40901));
        assertTrue(repository.existsById(chosen.getId()));
    }

    @Test
    void farOutPageReturnsEmptyWithoutWrappingToEarlierBills() throws Exception {
        insert();
        mvc.perform(get("/api/bill").param("page", "2147483647").param("size", "50"))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    private Bill insert() {
        Bill bill = new Bill();
        bill.setAmount(new BigDecimal("60.00"));
        bill.setCategory("餐饮");
        bill.setOccurredAt("2026-11-18 12:30:00");
        bill.setTimePrecision("EXACT");
        bill.setCreatedAt("2026-11-18 12:31:00");
        return repository.saveAndFlush(bill);
    }
}
