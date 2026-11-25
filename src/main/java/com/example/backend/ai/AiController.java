package com.example.backend.ai;

import com.example.backend.common.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.List;
import java.util.Set;

/**
 * AI 相关接口，统一挂在 /api/ai 前缀下。
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiClient aiClient;

    private final ChatRecords records;

    public AiController(AiClient aiClient, ChatRecords records) {
        this.aiClient = aiClient;
        this.records = records;
    }
    @PostMapping("/advice")
    public ApiResponse<String> advice(@RequestBody Map<String, Object> body) {
        Object raw = body.get("stats");
        if (raw == null || String.valueOf(raw).isBlank() || "null".equals(String.valueOf(raw))) {
            return ApiResponse.fail(40001, "缺少统计数据");
        }
        String data = raw instanceof String s ? s : String.valueOf(raw);
        try {
            return ApiResponse.ok(aiClient.advice(data));
        } catch (Exception e) {
            return ApiResponse.fail(50002, "生成建议失败：" + e.getMessage());
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatPlan>> planChat(@RequestBody ChatPlan.Request request) {
        if (request.text() == null || request.text().isBlank() || request.text().length() > 4000) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(40001, "请输入 1 至 4000 字的消息"));
        }
        List<ChatPlan.Turn> history = request.history() == null ? List.of() : request.history();
        int start = Math.max(0, history.size() - 18);
        int userTurns = 0;
        for (int i = history.size() - 1; i >= start; i--) {
            ChatPlan.Turn turn = history.get(i);
            if (turn != null && "user".equals(turn.role()) && ++userTurns == 6) {
                start = i;
                break;
            }
        }
        history = history.subList(start, history.size()).stream()
                .filter(turn -> turn != null && ("assistant".equals(turn.role()) || "user".equals(turn.role()))
                        && turn.content() != null)
                .map(turn -> new ChatPlan.Turn(turn.role(), turn.content().substring(0, Math.min(4000, turn.content().length()))))
                .toList();
        ChatPlan.Pending pending = ChatPlan.cleanPending(request.pending());
        try {
            return ResponseEntity.ok(ApiResponse.ok(ChatPlan.validate(
                    aiClient.planChat(new ChatPlan.Request(request.text(), history, pending)), pending)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(502).body(ApiResponse.fail(50002, "AI 暂时无法解析，请稍后重试"));
        }
    }

    @PostMapping("/chat/delete-filter")
    public ResponseEntity<ApiResponse<AiClient.DeleteBillFilter>> deleteFilter(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        if (text == null || text.isBlank() || text.length() > 4000) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(40001, "请输入 1 至 4000 字的消息"));
        }
        try {
            return ResponseEntity.ok(ApiResponse.ok(aiClient.planBillDeletion(text)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(502).body(ApiResponse.fail(50002, "AI 暂时无法识别删除条件"));
        }
    }

    @PostMapping("/chat/records")
    public ResponseEntity<ApiResponse<ChatRecords.Receipt>> record(@RequestBody ChatRecords.Request request) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(records.save(request)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(40001, e.getMessage()));
        } catch (ChatRecords.Conflict e) {
            return ResponseEntity.status(409).body(ApiResponse.fail(40901, "该动作 ID 已用于另一笔账单"));
        }
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiResponse<Void>> invalidJson() {
        return ResponseEntity.badRequest().body(ApiResponse.fail(40001, "请求体格式不正确"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> uploadTooLarge() {
        return ResponseEntity.status(413).body(ApiResponse.fail(40001, "小票图片不能超过 10 MB"));
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

    /**
     * 小票 / 截图识别。
     * 请求为 multipart/form-data，字段名 file，支持 jpg/png/webp。
     */
    @PostMapping(value = "/ocr-receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BillDraft> ocrReceipt(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResponse.fail(40001, "文件为空");
        }
        if (file.getSize() > 10 * 1024 * 1024 || file.getContentType() == null
                || !Set.of("image/jpeg", "image/png", "image/webp").contains(file.getContentType())) {
            return ApiResponse.fail(40001, "请选择 10 MB 以内的 JPG、PNG 或 WebP 图片");
        }
        try {
            return ApiResponse.ok(aiClient.parseReceipt(file));
        } catch (Exception e) {
            return ApiResponse.fail(50002, "识别失败：" + e.getMessage());
        }
    }

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