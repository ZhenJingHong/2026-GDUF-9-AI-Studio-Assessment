package com.example.backend.bill;

import com.example.backend.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

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

        // 2. 校验 category 必须在九个枚举内
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

    /** 查询账单列表，支持按月份、分类、关键词筛选 */
    @GetMapping
    public ApiResponse<List<Bill>> list(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {

        String start = null;
        String end = null;

        // 按 month (yyyy-MM) 拼出 start 和 end
        if (month != null && !month.isEmpty()) {
            start = month + "-01 00:00:00";
            try {
                java.time.YearMonth ym = java.time.YearMonth.parse(month);
                end = month + "-" + ym.lengthOfMonth() + " 23:59:59";
            } catch (Exception e) {
                // 如果前端传的 month 格式不对，忽略月份筛选
                start = null; end = null;
            }
        }

        //按 category 和 keyword 筛选，调用Repository 组合查询
        List<Bill> bills = repository.findBillsWithConditions(start, end, category, keyword);

        return ApiResponse.ok(bills);
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