package com.example.backend.ai;

import com.example.backend.bill.Bill;
import com.example.backend.bill.BillRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
class ChatRecords {
    record Request(UUID actionId, BillInput bill) {}
    record BillInput(BigDecimal amount, String category, String occurredAt, String merchant, String note, String timePrecision) {}
    record Receipt(Long billId) {}

    private final BillRepository bills;
    private final EntityManager entities;
    private final TransactionTemplate transaction;
    private final JsonMapper mapper;

    ChatRecords(BillRepository bills, EntityManager entities, PlatformTransactionManager manager, JsonMapper mapper) {
        this.bills = bills;
        this.entities = entities;
        this.transaction = new TransactionTemplate(manager);
        this.mapper = mapper;
    }

    synchronized Receipt save(Request request) {
        if (request == null || request.actionId() == null || request.bill() == null) {
            throw new IllegalArgumentException("请提供 actionId 和账单");
        }
        BillInput input = request.bill();
        BillDraft draft = ChatPlan.clean(new BillDraft(input.amount(), input.category(), input.merchant(),
                input.occurredAt(), input.note(), null));
        if (!ChatPlan.complete(draft) || input.merchant() != null && input.merchant().length() > 64
                || input.note() != null && input.note().length() > 255) {
            throw new IllegalArgumentException("请输入有效的金额（最多两位小数）、分类和消费日期，并检查商户和备注长度");
        }
        String digest = digest(new BillInput(draft.amount().setScale(2), input.category(), input.occurredAt(),
                input.merchant(), input.note(), input.timePrecision()));
        return transaction.execute(status -> {
            ChatRecordAction action = entities.find(ChatRecordAction.class, request.actionId());
            if (action != null) {
                if (!digest.equals(action.requestDigest)) throw new Conflict();
                return new Receipt(action.billId);
            }

            Bill bill = new Bill();
            bill.setAmount(draft.amount());
            bill.setCategory(draft.category());
            bill.setOccurredAt(draft.occurredAt());
            bill.setTimePrecision("EXACT".equals(input.timePrecision()) ? "EXACT" : "DATE");
            bill.setMerchant(draft.merchant());
            bill.setNote(draft.note());
            bill.setCreatedAt(LocalDateTime.now(ChatPlan.SHANGHAI).format(ChatPlan.TIME));
            action = new ChatRecordAction();
            action.actionId = request.actionId();
            action.requestDigest = digest;
            action.billId = bills.save(bill).getId();
            entities.persist(action);
            return new Receipt(action.billId);
        });
    }

    private String digest(BillInput bill) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(bill)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static class Conflict extends RuntimeException {}
}
