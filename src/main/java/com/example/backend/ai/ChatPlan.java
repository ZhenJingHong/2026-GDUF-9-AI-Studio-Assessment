package com.example.backend.ai;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Set;

record ChatPlan(Kind kind, String message, BillDraft draft, QuerySpec query, Pending pending) {
    static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Set<String> CATEGORIES = Set.of("餐饮", "交通", "购物", "娱乐", "居住", "医疗", "教育", "其他");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    /**
     * 模型自行判定的越界请求统一使用这句话术。
     * 拒答话术不接受模型输出，避免被「请你用友好语气说可以」这类话术改写。
     */
    private static final String REFUSAL = "这个请求超出记账助手的职责范围，我不能执行。可以告诉我一笔真实消费，或者问我某段时间的消费情况。";

    enum Kind { CLARIFY, RECORD, QUERY, RECORD_THEN_QUERY, REFUSE, UNSUPPORTED }
    enum Source { TEXT, RECEIPT }
    record Turn(String role, String content) {}
    record Pending(Source source, BillDraft draft, QuerySpec query) {}
    record Request(String text, List<Turn> history, Pending pending) {}

    static BillDraft clean(BillDraft draft) {
        if (draft == null) return null;
        BigDecimal amount = draft.amount();
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0
                || amount.stripTrailingZeros().scale() > 2) {
            amount = null;
        }
        return new BillDraft(amount, category(draft.category()), bounded(draft.merchant(), 64),
                timestamp(draft.occurredAt()), bounded(draft.note(), 255), draft.confidence());
    }

    static boolean complete(BillDraft draft) {
        return draft != null && draft.amount() != null && draft.category() != null && draft.occurredAt() != null;
    }

    static Pending cleanPending(Pending pending) {
        if (pending == null || pending.source() == null) return null;
        return new Pending(pending.source(), clean(pending.draft()), cleanQuery(pending.query()));
    }

    static ChatPlan validate(ChatPlan plan, Pending pending) {
        if (plan == null || plan.kind == null) throw new IllegalArgumentException("模型未返回有效意图");
        String message = bounded(plan.message, 500);
        // 越界请求：丢弃模型给的草稿和话术，只保留固定拒答文本，防止「先拒答再偷偷入账」
        if (plan.kind == Kind.REFUSE) {
            return new ChatPlan(Kind.REFUSE, REFUSAL, null, null, pending);
        }
        if (plan.kind == Kind.UNSUPPORTED) {
            return new ChatPlan(Kind.UNSUPPORTED, message == null ? "可以试着告诉我一笔实际消费，或查询已有账单。" : message,
                    null, null, pending);
        }
        QuerySpec query = cleanQuery(plan.query);
        if (query == null && pending != null) query = pending.query();
        if (plan.kind == Kind.QUERY) {
            return query == null
                    ? new ChatPlan(Kind.CLARIFY, "请告诉我想查哪段时间的消费。", null, null, pending)
                    : new ChatPlan(Kind.QUERY, message, null, query, pending);
        }

        Pending proposed = cleanPending(plan.pending);
        BillDraft proposedDraft = plan.draft != null ? plan.draft : plan.pending == null ? null : plan.pending.draft();
        BillDraft draft = merge(pending == null ? null : pending.draft(), proposedDraft);
        if (query == null && proposed != null) query = proposed.query();
        Source source = pending == null ? Source.TEXT : pending.source();
        Pending next = new Pending(source, draft, query);
        if (!complete(draft)) {
            if (draft != null || plan.kind != Kind.CLARIFY) message = missing(draft);
            return new ChatPlan(Kind.CLARIFY, message == null ? "请补充消费信息。" : message, draft, query, next);
        }
        if (plan.kind == Kind.CLARIFY || source == Source.RECEIPT || (plan.kind == Kind.RECORD_THEN_QUERY && query == null)) {
            return new ChatPlan(Kind.CLARIFY, source == Source.RECEIPT ? "请核对小票信息，确认后入账。"
                    : message == null ? "请补充要查询的日期范围。" : message, draft, query, next);
        }
        return new ChatPlan(query == null ? Kind.RECORD : Kind.RECORD_THEN_QUERY, message, draft, query, next);
    }

    private static BillDraft merge(BillDraft previous, BillDraft change) {
        if (previous == null) return clean(change);
        if (change == null) return previous;
        return clean(new BillDraft(value(change.amount(), previous.amount()), value(change.category(), previous.category()),
                value(change.merchant(), previous.merchant()), value(change.occurredAt(), previous.occurredAt()),
                value(change.note(), previous.note()), change.confidence()));
    }

    private static <T> T value(T edited, T previous) {
        return edited == null ? previous : edited;
    }

    private static String missing(BillDraft draft) {
        java.util.ArrayList<String> fields = new java.util.ArrayList<>();
        if (draft == null || draft.amount() == null) fields.add("消费金额");
        if (draft == null || draft.category() == null) fields.add("消费分类");
        if (draft == null || draft.occurredAt() == null) fields.add("消费日期");
        return "请补充" + String.join("、", fields) + "。";
    }

    private static QuerySpec cleanQuery(QuerySpec query) {
        if (query == null || !("stat".equals(query.action()) || "list".equals(query.action()))) return null;
        String start = date(query.start());
        String end = date(query.end());
        if (start == null || end == null || start.compareTo(end) > 0
                || (query.category() != null && category(query.category()) == null)) return null;
        String aggregate = query.aggregate() == null ? "sum" : query.aggregate();
        if ("stat".equals(query.action()) && !Set.of("sum", "count", "avg", "max").contains(aggregate)) return null;
        return new QuerySpec(query.action(), start, end, query.category(), "stat".equals(query.action()) ? aggregate : null,
                bounded(query.keyword(), 255));
    }

    private static String category(String category) {
        return category != null && CATEGORIES.contains(category) ? category : null;
    }

    private static String date(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || value.startsWith("0000")) return null;
        try {
            return LocalDate.parse(value).toString();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String timestamp(String value) {
        if (value == null) return null;
        if (date(value) != null) return value + " 00:00:00";
        if (value.length() != 19 || date(value.substring(0, 10)) == null) return null;
        try {
            return LocalDateTime.parse(value.replace('T', ' '), TIME).format(TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String bounded(String value, int max) {
        return value == null || value.isBlank() ? null : value.strip().substring(0, Math.min(max, value.strip().length()));
    }
}
