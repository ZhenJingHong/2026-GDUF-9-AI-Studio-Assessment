package com.example.backend.budget;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;

import com.example.backend.budget.BudgetService.BudgetMonth;
import com.example.backend.common.ApiResponse;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/budget")
public class BudgetController {

    private static final Pattern MONTH = Pattern.compile("[0-9]{4}-[0-9]{2}");
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("100000000.00");

    private final BudgetService service;
    private final ObjectReader requestReader;

    BudgetController(BudgetService service, JsonMapper mapper) {
        this.service = service;
        this.requestReader = mapper.readerFor(JsonNode.class)
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<BudgetMonth>> get(@RequestParam(required = false) String month) {
        YearMonth parsedMonth = parseMonth(month);
        if (parsedMonth == null) {
            return invalidMonth();
        }
        return ResponseEntity.ok(ApiResponse.ok(service.findMonth(parsedMonth)));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<BudgetMonth>> save(@RequestBody String body) {
        JsonNode request = requestReader.readValue(body);
        if (request == null || !request.isObject()) {
            return invalidJson();
        }

        JsonNode monthNode = request.path("month");
        YearMonth month = parseMonth(monthNode.isString() ? monthNode.stringValue() : null);
        if (month == null) {
            return invalidMonth();
        }

        JsonNode amountNode = request.path("amount");
        BigDecimal amount = amountNode.isNumber() ? amountNode.decimalValue() : null;
        if (amount == null || amount.compareTo(MIN_AMOUNT) < 0 || amount.compareTo(MAX_AMOUNT) > 0
                || amount.stripTrailingZeros().scale() > 2) {
            return badRequest(40001, "金额必须为 0.01 至 100000000.00 的数字，最多两位有效小数");
        }

        JsonNode categoryNode = request.path("category");
        String category = null;
        if (!categoryNode.isMissingNode() && !categoryNode.isNull()) {
            if (!categoryNode.isString() || !BudgetService.CATEGORIES.contains(categoryNode.stringValue())) {
                return badRequest(40002, "分类不在枚举内");
            }
            category = categoryNode.stringValue();
        }

        return ResponseEntity.ok(ApiResponse.ok(service.save(month, category, amount.setScale(2))));
    }

    @ExceptionHandler({JacksonException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiResponse<BudgetMonth>> invalidJson() {
        return badRequest(40001, "请求体必须是有效的 JSON 对象");
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || !MONTH.matcher(month).matches() || month.startsWith("0000")) {
            return null;
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static ResponseEntity<ApiResponse<BudgetMonth>> invalidMonth() {
        return badRequest(40001, "月份必须为有效的 YYYY-MM");
    }

    private static ResponseEntity<ApiResponse<BudgetMonth>> badRequest(int code, String message) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(code, message));
    }
}
