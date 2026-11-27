package com.example.backend.bill;

import com.example.backend.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

/**
 * 账单接口，前缀 /api/bill。
 */
@RestController
@RequestMapping("/api/bill")
public class BillController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 固定的9个分类枚举（与接口文档完全一致）
    private static final List<String> CATEGORIES = List.of("餐饮", "交通", "购物", "娱乐", "居住", "医疗", "教育", "其他");

    private final BillRepository repository;

    // 构造器注入
    public BillController(BillRepository repository) {
        this.repository = repository;
    }

    /** 新增账单 */
    @PostMapping
    public ApiResponse<Bill> create(@RequestBody Bill bill) {
        // 1. 校验 amount 不能为空、不能为负
        if (bill.getAmount() == null || bill.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return ApiResponse.fail(40001, "金额必须大于0");
        }

        // 2. 校验 category 必须在八个枚举内
        if (bill.getCategory() == null || !CATEGORIES.contains(bill.getCategory())) {
            return ApiResponse.fail(40002, "分类不在枚举内");
        }

        // 3. 补全时间字段
        String now = LocalDateTime.now().format(TIME);
        if (bill.getCreatedAt() == null) {
            bill.setCreatedAt(now);
        }
        if (bill.getOccurredAt() == null) {
            bill.setOccurredAt(bill.getCreatedAt());
        }

        // 4. 保存入库并返回
        return ApiResponse.ok(repository.save(bill));
    }

    @Transactional
    @PutMapping("/{id}/amount")
    public ApiResponse<Bill> updateAmount(@PathVariable Long id, @RequestBody Map<String, BigDecimal> body) {
        BigDecimal amount = body.get("amount");
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2
                || amount.compareTo(new BigDecimal("9999999999.99")) > 0) {
            return ApiResponse.fail(40001, "请输入有效金额，最多两位小数");
        }
        return repository.findForUpdate(id).map(bill -> {
            bill.setAmount(amount);
            return ApiResponse.ok(repository.save(bill));
        }).orElseGet(() -> ApiResponse.fail(40401, "原账单不存在，无法更正"));
    }

    @GetMapping
    public ApiResponse<List<Bill>> list(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(slice(matchingBills(month, category, keyword), Math.max(1, page), pageSize(size)));
    }

    record BillPage(List<Bill> items, int page, int size, int total, BigDecimal amount) {}

    @GetMapping("/page")
    public ApiResponse<BillPage> listPage(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Bill> matches = matchingBills(month, category, keyword);
        int limit = pageSize(size);
        int total = matches.size();
        int lastPage = total == 0 ? 1 : (total - 1) / limit + 1;
        int current = Math.max(1, Math.min(page, lastPage));
        BigDecimal amount = matches.stream().map(Bill::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return ApiResponse.ok(new BillPage(slice(matches, current, limit), current, limit, total, amount));
    }

    private List<Bill> matchingBills(String month, String category, String keyword) {
        String start = null;
        String end = null;
        if (month != null && !month.isEmpty()) {
            try {
                java.time.YearMonth period = java.time.YearMonth.parse(month);
                start = period.atDay(1) + " 00:00:00";
                end = period.atEndOfMonth() + " 23:59:59";
            } catch (java.time.format.DateTimeParseException ignored) {
                // Preserve the legacy list behavior when a month is invalid.
            }
        }
        return repository.findBillsWithConditions(start, end, category, keyword);
    }

    private static int pageSize(int size) {
        return size < 1 ? 10 : Math.min(size, 50);
    }

    private static List<Bill> slice(List<Bill> rows, int page, int size) {
        long start = (page - 1L) * size;
        if (start >= rows.size()) return List.of();
        return rows.subList((int) start, (int) Math.min(start + size, rows.size()));
    }

    record DeleteSnapshot(BigDecimal amount, String occurredAt, String category, String merchant,
                          String note, String createdAt, String timePrecision) {
        boolean matches(Bill bill) {
            return amount != null && bill.getAmount() != null && bill.getAmount().compareTo(amount) == 0
                    && Objects.equals(occurredAt, bill.getOccurredAt())
                    && Objects.equals(category, bill.getCategory())
                    && Objects.equals(merchant, bill.getMerchant())
                    && Objects.equals(note, bill.getNote())
                    && Objects.equals(createdAt, bill.getCreatedAt())
                    && Objects.equals(timePrecision, bill.getTimePrecision());
        }
    }

    @Transactional
    @DeleteMapping("/{id}/confirmed")
    public ApiResponse<Void> deleteConfirmed(@PathVariable Long id, @RequestBody DeleteSnapshot snapshot) {
        Bill bill = repository.findForUpdate(id).orElse(null);
        if (bill == null || !snapshot.matches(bill)) {
            return ApiResponse.fail(40901, "账单已变化或不存在，请重新查找再确认");
        }
        repository.delete(bill);
        return ApiResponse.ok(null);
    }

    /** 删除账单 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ApiResponse.fail(40401, "账单不存在");
        }
        repository.deleteById(id);
        return ApiResponse.ok(null);
    }
}