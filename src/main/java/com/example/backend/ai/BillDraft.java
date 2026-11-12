package com.example.backend.ai;

import java.math.BigDecimal;

/**
 * 从一句话或小票中解析出来的账单草稿，尚未入库。
 * <p>
 * 该对象由大模型输出，字段结构固定但值不一定可靠：
 * 前端必须先让用户确认或修正，再调用 /api/bill 入库，
 * 不允许把草稿直接写进数据库。
 *
 * @param amount     金额（元）。用 BigDecimal 而非 double，避免浮点误差
 * @param category   分类，必须是约定的分类之一，无法判断时为 null
 * @param merchant   商户名，可能为 null
 * @param occurredAt 消费时间，格式 yyyy-MM-dd HH:mm:ss，可能为 null
 * @param note       原文摘要
 * @param confidence 模型自评置信度，0~1，仅作参考
 */
public record BillDraft(
        BigDecimal amount,
        String category,
        String merchant,
        String occurredAt,
        String note,
        Double confidence
) {
}
