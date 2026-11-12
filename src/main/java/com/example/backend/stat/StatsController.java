package com.example.backend.stat;

import com.example.backend.common.ApiResponse;

import java.time.YearMonth;
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

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiResponse<Void> missingParameter(MissingServletRequestParameterException exception) {
        return ApiResponse.fail(40001, "缺少参数 " + exception.getParameterName());
    }

    @ExceptionHandler(InvalidStatParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ApiResponse<Void> invalidParameter(InvalidStatParameterException exception) {
        return ApiResponse.fail(40001, exception.getMessage());
    }

    private static YearMonth parseMonth(String parameter, String value) {
        if (!MONTH_FORMAT.matcher(value).matches() || value.startsWith("0000-")) {
            throw new InvalidStatParameterException(parameter + " 必须为有效的 yyyy-MM 月份");
        }
        return YearMonth.parse(value);
    }

    private static class InvalidStatParameterException extends RuntimeException {
        InvalidStatParameterException(String message) {
            super(message);
        }
    }
}
