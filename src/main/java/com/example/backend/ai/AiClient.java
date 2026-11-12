package com.example.backend.ai;

import com.example.backend.common.BillCategories;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 大模型 API 客户端，项目里所有与模型通信的代码都集中在这里。
 * <p>
 * 职责边界：
 * <ul>
 *   <li>只负责「发请求、拿响应、转对象」，不处理业务校验（校验在上层）</li>
 *   <li>接口地址、密钥、模型名全部来自 application.properties，不硬编码</li>
 * </ul>
 * <p>
 * 注意：Spring Boot 4 使用 Jackson 3，包名是 tools.jackson.*（Spring Boot 3 是 com.fasterxml.jackson.*）。
 */
@Component
public class AiClient {

    /** Jackson 3 的 JSON 解析器，线程安全，作为常量全局复用 */
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    /**
     * 一句话记账的提示词。
     * <p>
     * 设计要点：
     * <ul>
     *   <li>字段逐个列出，减少模型自由发挥</li>
     *   <li>明确「不确定填 null」，否则模型倾向编造一个数</li>
     *   <li>给两个示例（few-shot），实测能显著提高格式稳定性</li>
     *   <li>必须出现 "JSON" 字样，这是 response_format=json_object 的硬性要求</li>
     * </ul>
     */
    private static final String PARSE_BILL_PROMPT = """
            你是一个记账助手。用户会用一句中文描述一笔消费，请把它解析成一个 JSON 对象。
            只输出 JSON 对象，不要输出任何解释，不要用 markdown 代码块。

            JSON 字段：
            - amount：数字，金额（单位：元）。无法确定时填 null。
            - category：字符串，只能是以下分类之一：餐饮、交通、购物、娱乐、居住、医疗、教育、其他。无法确定时填 null。
            - merchant：字符串，商户名。没有提到时填 null。
            - occurredAt：字符串，消费时间，格式 yyyy-MM-dd HH:mm:ss。没有提到时填 null。
            - note：字符串，原文的简短摘要。
            - confidence：数字，0 到 1，表示你对这次解析的置信度。

            规则：
            1. 金额单位默认元，出现“块”“元”“块钱”都按元处理。
            2. 不确定的字段一律填 null，不要编造。
            3. 如果原文提到“AA”，金额按平摊后的处理；无法确定时保留原金额并在 note 中说明。

            示例：
            输入：昨天打车32块
            输出：{"amount":32,"category":"交通","merchant":null,"occurredAt":null,"note":"打车","confidence":0.95}

            输入：星巴克咖啡38
            输出：{"amount":38,"category":"餐饮","merchant":"星巴克","occurredAt":null,"note":"咖啡","confidence":0.9}
            """;

    /** HTTP 客户端，线程安全可复用 */
    private final RestClient restClient;

    /** 模型名，如 deepseek-chat */
    private final String model;

    /**
     * 构造器注入：Spring 启动时从 application.properties 读
     * ai.base-url / ai.api-key / ai.model 并传进来。
     * <p>
     * apiKey 来自环境变量 AI_API_KEY，不写进配置文件，避免提交时泄露。
     */
    public AiClient(@Value("${ai.base-url}") String baseUrl,
                    @Value("${ai.api-key}") String apiKey,
                    @Value("${ai.model}") String model) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.model = model;
    }

    /** 普通对话，返回纯文本，仅用于 /api/ai/test 自测链路是否通 */
    public String chat(String prompt) {
        return request(null, prompt, false);
    }

    /**
     * 把一句话解析成账单草稿。
     *
     * @param text 用户原话，如「昨天打车32块，和同事AA」
     * @return 解析结果，字段可能为 null，由前端让用户确认
     * @throws IllegalStateException 模型返回的不是合法 JSON 时抛出
     */
    public BillDraft parseBill(String text) {
        String json = request(PARSE_BILL_PROMPT, text, true);
        try {
            BillDraft draft = JSON_MAPPER.readValue(json, BillDraft.class);
            // 兜底：模型可能输出「出行」「吃饭」这类不在枚举里的值，
            // 统一归为「其他」，防止脏数据流进数据库
            if (draft.category() != null && !BillCategories.contains(draft.category())) {
                draft = new BillDraft(draft.amount(), "其他", draft.merchant(),
                        draft.occurredAt(), draft.note(), draft.confidence());
            }
            return draft;
        } catch (Exception e) {
            throw new IllegalStateException("模型返回的不是合法 JSON：" + json, e);
        }
    }

    /**
     * 真正发 HTTP 请求的地方。
     *
     * @param systemPrompt 系统提示词，null 表示不传
     * @param userPrompt   用户输入
     * @param jsonMode     是否要求模型只输出 JSON
     */
    @SuppressWarnings("unchecked")
    private String request(String systemPrompt, String userPrompt, boolean jsonMode) {
        List<Map<String, Object>> messages = systemPrompt == null
                ? List.of(Map.of("role", "user", "content", userPrompt))
                : List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        // temperature 越低输出越稳定；解析任务要的是准确，不是创意
        requestBody.put("temperature", 0.2);
        requestBody.put("messages", messages);
        if (jsonMode) {
            // 强制模型输出合法 JSON，避免「好的，我来帮你分析」这类废话
            requestBody.put("response_format", Map.of("type", "json_object"));
        }

        // 注意：base-url 只写域名，路径全放在 uri 里。
        // 如果 uri 以 / 开头会替换掉 baseUrl 的路径部分，写错会 404
        Map<String, Object> response = restClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        // OpenAI 兼容格式的响应：choices[0].message.content 才是模型回答
        List<Map<String, Object>> choices =
                (List<Map<String, Object>>) response.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }
    /**
     * 把自然语言查询解析成结构化查询条件。
     * <p>
     * 关键设计：模型只负责「翻译成查询参数」，不负责算数字。
     * 统计结果必须由数据库算出来，否则模型可能编造金额。
     */
    public QuerySpec parseQuery(String text) {
        LocalDate today = LocalDate.now();
        String todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String thisMonthStart = today.withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        LocalDate lastMonthEnd = today.withDayOfMonth(1).minusDays(1);
        String lastMonthStart = lastMonthEnd.withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String lastMonthEndStr = lastMonthEnd.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String weekAgo = today.minusDays(6).format(DateTimeFormatter.ISO_LOCAL_DATE);

        String prompt = String.format("""
                你是一个记账应用的查询解析器。今天是 %1$s。
                用户会用一句中文问自己的消费情况，请把它解析成一个 JSON 对象。
                只输出 JSON 对象，不要输出任何解释。

                JSON 字段：
                - action：字符串，"stat" 表示要统计数字，"list" 表示要看明细，"unknown" 表示与记账无关或看不懂
                - start：字符串，起始日期 yyyy-MM-dd。用户没提时间范围时填 null
                - end：字符串，结束日期 yyyy-MM-dd，含当天。用户没提时间范围时填 null
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
            if (spec.category() != null && !BillCategories.contains(spec.category())) {
                spec = new QuerySpec(spec.action(), spec.start(), spec.end(),
                        null, spec.aggregate(), spec.keyword());
            }
            return spec;
        } catch (Exception e) {
            throw new IllegalStateException("模型返回的不是合法 JSON：" + json, e);
        }
    }
}
