package com.example.backend.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 大模型 API 客户端，项目里所有与模型通信的代码都集中在这里。
 * <p>
 * 注意：Spring Boot 4 使用 Jackson 3，包名是 tools.jackson.*。
 */
@Component
public class AiClient {

    /** Jackson 3 的 JSON 解析器，线程安全，作为常量全局复用 */
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    /** 统一的日期格式 */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    /** 允许的分类枚举，与 docs/接口文档.md 一致 */
    private static final Set<String> CATEGORIES = Set.of(
            "餐饮", "交通", "购物", "娱乐", "居住", "医疗", "教育", "其他");

    /**
     * 一句话记账的提示词。
     */
    private static final String PARSE_BILL_PROMPT = """
            你是一个记账助手。今天是 %1$s。

            用户会用一句中文描述一笔消费，请把它解析成一个 JSON 对象。
            只输出 JSON 对象，不要输出任何解释，不要用 markdown 代码块。

            时间规则（今天是 %1$s）：
            - "今天" 用 %1$s
            - "昨天" 用 %2$s
            - "前天" 用 %3$s
            - 只说了日期没说时刻，用 00:00:00 补全
            - 完全没提到时间，occurredAt 填 null

            JSON 字段：
            - amount：数字，金额（单位：元）。无法确定时填 null。
            - category：字符串，只能是以下九个之一：餐饮、交通、购物、娱乐、居住、医疗、教育、其他。无法确定时填 null。
            - merchant：字符串，商户名。没有提到时填 null。
            - occurredAt：字符串，消费时间，格式 yyyy-MM-dd HH:mm:ss。按上面的时间规则换算。
            - note：字符串，原文的简短摘要。
            - confidence：数字，0 到 1，表示你对这次解析的置信度。

            规则：
            1. 金额单位默认元，出现“块”“元”“块钱”都按元处理。
            2. 不确定的字段一律填 null，不要编造。
            3. 如果原文提到“AA”，金额按平摊后的处理；无法确定时保留原金额并在 note 中说明。

            示例：
            输入：昨天打车32块
            输出：{"amount":32,"category":"交通","merchant":null,"occurredAt":"%2$s 00:00:00","note":"打车","confidence":0.95}

            输入：星巴克咖啡38
            输出：{"amount":38,"category":"餐饮","merchant":"星巴克","occurredAt":null,"note":"咖啡","confidence":0.9}
            """;

    /**
     * 小票识别的提示词。
     * <p>
     * 关键约束：优先取"实付/合计"，不要取单价；识别不清不要猜；
     * 不是小票的图片返回全 null，避免模型强行编一个金额。
     */
    private static final String RECEIPT_PROMPT = """
            你是一个记账助手。用户会给你一张小票或支付截图，请识别出这笔消费并解析成 JSON 对象。
            只输出 JSON 对象，不要输出任何解释。

            JSON 字段：
            - amount：数字，这笔订单的实付总金额（单位：元）。识别不出填 null。
            - category：字符串，只能是以下九个之一：餐饮、交通、购物、娱乐、居住、医疗、教育、其他。识别不出填 null。
            - merchant：字符串，商户名称。没有填 null。
            - occurredAt：字符串，消费时间，格式 yyyy-MM-dd HH:mm:ss。小票上没有时间就填 null。
            - note：字符串，简要说明买了什么。
            - confidence：数字，0 到 1，表示识别置信度。

            规则：
            1. 金额优先取“实付”“合计”“总计”“应付”，不要取单价或找零。
            2. 图片不是小票或账单时，所有字段填 null，confidence 填 0。
            3. 数字看不清就填 null，不要猜测。
            """;

    /** HTTP 客户端，线程安全可复用 */
    private final RestClient restClient;

    /** 模型名，如 deepseek-flash */
    private final String model;

    public AiClient(@Value("${ai.base-url}") String baseUrl,
                    @Value("${ai.api-key}") String apiKey,
                    @Value("${ai.model}") String model) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.model = model;
    }

    /** 普通对话，返回纯文本，仅用于自测链路 */
    public String chat(String prompt) {
        return request(null, prompt, false);
    }

    /**
     * 把一句话解析成账单草稿。
     */
    public BillDraft parseBill(String text) {
        LocalDate today = LocalDate.now();
        String prompt = String.format(PARSE_BILL_PROMPT,
                today.format(DATE),
                today.minusDays(1).format(DATE),
                today.minusDays(2).format(DATE));
        String json = request(prompt, text, true);
        return toBillDraft(json);
    }

    /**
     * 识别小票 / 截图，解析成账单草稿。
     * <p>
     * 实现方式：图片转 base64 后以 data URL 内联，
     * 和文本一起作为 content 数组发给多模态模型（DeepSeek deepseek-flash 支持）。
     */
    public BillDraft parseReceipt(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("读取上传文件失败", e);
        }
        String base64 = Base64.getEncoder().encodeToString(bytes);
        String mime = file.getContentType() == null ? "image/jpeg" : file.getContentType();
        String dataUrl = "data:" + mime + ";base64," + base64;

        Map<String, Object> textPart = Map.of("type", "text", "text", RECEIPT_PROMPT);
        Map<String, Object> imagePart = Map.of(
                "type", "image_url",
                "image_url", Map.of("url", dataUrl));
        List<Map<String, Object>> content = List.of(textPart, imagePart);

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", content);

        String json = requestMessages(List.of(userMessage), true);
        return toBillDraft(json);
    }

    /** 把模型返回的 JSON 字符串转成 BillDraft，并做分类兜底 */
    private BillDraft toBillDraft(String json) {
        try {
            BillDraft draft = JSON_MAPPER.readValue(json, BillDraft.class);
            // 兜底：模型可能输出不在枚举里的分类，统一归为「其他」
            if (draft.category() != null && !CATEGORIES.contains(draft.category())) {
                draft = new BillDraft(draft.amount(), "其他", draft.merchant(),
                        draft.occurredAt(), draft.note(), draft.confidence());
            }
            return draft;
        } catch (Exception e) {
            throw new IllegalStateException("模型返回的不是合法 JSON：" + json, e);
        }
    }

    /**
     * 把自然语言查询解析成结构化查询条件。
     */
    public QuerySpec parseQuery(String text) {
        LocalDate today = LocalDate.now();
        String todayStr = today.format(DATE);
        String thisMonthStart = today.withDayOfMonth(1).format(DATE);
        LocalDate lastMonthEnd = today.withDayOfMonth(1).minusDays(1);
        String lastMonthStart = lastMonthEnd.withDayOfMonth(1).format(DATE);
        String lastMonthEndStr = lastMonthEnd.format(DATE);
        String weekAgo = today.minusDays(6).format(DATE);

        String prompt = String.format("""
                你是一个记账应用的查询解析器。今天是 %1$s。
                用户会用一句中文问自己的消费情况，请把它解析成一个 JSON 对象。
                只输出 JSON 对象，不要输出任何解释。

                JSON 字段：
                - action：字符串，"stat" 表示要统计数字，"list" 表示要看明细，"unknown" 表示与记账无关或看不懂
                - start：字符串，起始日期 yyyy-MM-dd。用户没提时间范围时填 null
                - end：字符串，结束日期 yyyy-MM-dd，含当天。没提时间范围时填 null
                - category：字符串，只能是以下之一：餐饮、交通、购物、娱乐、居住、医疗、教育、其他。没提分类时填 null
                - aggregate：字符串，"sum" 求和、"count" 计数、"max" 最大值、"avg" 平均值。action 不是 stat 时填 null
                - keyword：字符串，商户或备注关键词。没有时填 null

                时间规则（今天是 %1$s）：
                - "这个月" 从 %2$s 到 %1$s
                - "上个月" 从 %3$s 到 %4$s
                - "今天" 就是 %1$s 到 %1$s
                - "最近一周" 从 %5$s 到 %1$s

                示例：
                输入：上个月吃饭花了多少
                输出：{"action":"stat","start":"%3$s","end":"%4$s","category":"餐饮","aggregate":"sum","keyword":null}

                输入：这个月有哪些消费
                输出：{"action":"list","start":"%2$s","end":"%1$s","category":null,"aggregate":null,"keyword":null}

                输入：今天天气怎么样
                输出：{"action":"unknown","start":null,"end":null,"category":null,"aggregate":null,"keyword":null}
                """, todayStr, thisMonthStart, lastMonthStart, lastMonthEndStr, weekAgo);

        String json = request(prompt, text, true);
        try {
            QuerySpec spec = JSON_MAPPER.readValue(json, QuerySpec.class);
            if (spec.category() != null && !CATEGORIES.contains(spec.category())) {
                spec = new QuerySpec(spec.action(), spec.start(), spec.end(),
                        null, spec.aggregate(), spec.keyword());
            }
            return spec;
        } catch (Exception e) {
            throw new IllegalStateException("模型返回的不是合法 JSON：" + json, e);
        }
    }

    /** 纯文本请求的便捷方法 */
    private String request(String systemPrompt, String userPrompt, boolean jsonMode) {
        List<Map<String, Object>> messages = systemPrompt == null
                ? List.of(Map.of("role", "user", "content", userPrompt))
                : List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt));
        return requestMessages(messages, jsonMode);
    }

    /**
     * 发请求的核心方法，接收完整的 messages 数组（支持多模态 content）。
     */
    @SuppressWarnings("unchecked")
    private String requestMessages(List<Map<String, Object>> messages, boolean jsonMode) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.2);
        requestBody.put("messages", messages);
        if (jsonMode) {
            requestBody.put("response_format", Map.of("type", "json_object"));
        }

        Map<String, Object> response = restClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        List<Map<String, Object>> choices =
                (List<Map<String, Object>>) response.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }
}