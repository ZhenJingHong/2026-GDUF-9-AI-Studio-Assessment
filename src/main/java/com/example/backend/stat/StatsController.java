package com.example.backend.stat;

import com.example.backend.common.ApiResponse;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stat")
public class StatsController {
    private static final Pattern MONTH_FORMAT = Pattern.compile("[0-9]{4}-(0[1-9]|1[0-2])");
    private static final Pattern DATE_FORMAT = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final Set<String> CATEGORIES = Set.of(
            "餐饮", "交通", "购物", "娱乐", "居住", "医疗", "教育", "其他");
    private final StatsService service;

    public StatsController(StatsService service) {
        this.service = service;
    }

    @GetMapping("/category")
    public ApiResponse<StatsService.CategoryStats> byCategory(@RequestParam("month") String month) {
        return ApiResponse.ok(service.byCategory(parseMonth("month", month)));
    }

    @GetMapping("/trend")
    public ApiResponse<StatsService.MonthlyTrend> trend(
            @RequestParam("from") String from, @RequestParam("to") String to) {
        YearMonth startMonth = parseMonth("from", from);
        YearMonth endMonth = parseMonth("to", to);
        if (startMonth.isAfter(endMonth)) {
            throw new InvalidStatParameterException("from 不能晚于 to");
        }
        return ApiResponse.ok(service.trend(startMonth, endMonth));
    }

    @GetMapping("/range")
    public ApiResponse<StatsService.RangeStat> range(
            @RequestParam("start") String start,
            @RequestParam("end") String end,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "aggregate", defaultValue = "sum") String aggregate) {
        LocalDate startDate = parseDate("start", start);
        LocalDate endDate = parseDate("end", end);
        if (startDate.isAfter(endDate)) {
            throw new InvalidStatParameterException("start 不能晚于 end");
        }

        String categoryFilter = optionalFilter(category);
        if (categoryFilter != null && !CATEGORIES.contains(categoryFilter)) {
            throw new InvalidStatParameterException(40002, "category 不在分类枚举内");
        }
        return ApiResponse.ok(service.range(startDate, endDate, categoryFilter,
                optionalFilter(keyword), parseAggregate(aggregate)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiResponse<Void> missingParameter(MissingServletRequestParameterException exception) {
        return ApiResponse.fail(40001, "缺少参数 " + exception.getParameterName());
    }

    @ExceptionHandler(InvalidStatParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiResponse<Void> invalidParameter(InvalidStatParameterException exception) {
        return ApiResponse.fail(exception.code, exception.getMessage());
    }

    private static YearMonth parseMonth(String parameter, String value) {
        if (!MONTH_FORMAT.matcher(value).matches() || value.startsWith("0000-")) {
            throw new InvalidStatParameterException(parameter + " 必须为有效的 yyyy-MM 月份");
        }
        return YearMonth.parse(value);
    }

    private static LocalDate parseDate(String parameter, String value) {
        String message = parameter + " 必须为有效的 yyyy-MM-dd 日期";
        if (!DATE_FORMAT.matcher(value).matches() || value.startsWith("0000-")) {
            throw new InvalidStatParameterException(message);
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new InvalidStatParameterException(message);
        }
    }

    private static StatsService.RangeAggregate parseAggregate(String value) {
        try {
            return StatsService.RangeAggregate.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidStatParameterException("aggregate 必须是 sum、count、max 或 avg");
        }
    }

    private static String optionalFilter(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static class InvalidStatParameterException extends RuntimeException {
        private final int code;

        InvalidStatParameterException(String message) {
            this(40001, message);
        }

        InvalidStatParameterException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}
