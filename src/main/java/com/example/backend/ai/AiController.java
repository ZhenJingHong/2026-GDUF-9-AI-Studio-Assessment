package com.example.backend.ai;

import com.example.backend.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 相关接口，统一挂在 /api/ai 前缀下。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiClient aiClient;

    public AiController(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }

    @PostMapping("/test")
    public ApiResponse<String> test(@RequestBody Map<String, String> body) {
        String reply = aiClient.chat(body.getOrDefault("text", "用一句话介绍你自己"));
        return ApiResponse.ok(reply);
    }

    @PostMapping("/parse-bill")
    public ApiResponse<BillDraft> parseBill(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "").trim();
        if (text.isEmpty()) {
            return ApiResponse.fail(40001, "text 不能为空");
        }
        try {
            return ApiResponse.ok(aiClient.parseBill(text));
        } catch (Exception e) {
            return ApiResponse.fail(50002, "解析失败：" + e.getMessage());
        }
    }
    /** 自然语言查账：返回结构化查询条件，不是答案 */
    @PostMapping("/query")
    public ApiResponse<QuerySpec> query(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "").trim();
        if (text.isEmpty()) {
            return ApiResponse.fail(40001, "text 不能为空");
        }
        try {
            return ApiResponse.ok(aiClient.parseQuery(text));
        } catch (Exception e) {
            return ApiResponse.fail(50002, "解析失败：" + e.getMessage());
        }
    }
}