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

    /** 查询账单列表，支持按月份、分类、关键词筛选，以及安全分页 */
    @GetMapping
    public ApiResponse<List<Bill>> list(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            // 【新增】接收前端传来的分页参数，默认第1页，每页10条
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {

        // 强制限制 page 和 size

        // 1. 页码不能小于1（防止传 -1 或 0）
        if (page == null || page < 1) {
            page = 1;
        }
        // 2. 每页条数限制在 1 到 50 之间（防止恶意传 size=999999 把内存撑爆）
        if (size == null || size < 1) {
            size = 10;
        }
        if (size > 50) {
            size = 50;
        }

        String start = null;
        String end = null;

        // 处理 month 参数
        if (month != null && !month.isEmpty()) {
            start = month + "-01 00:00:00";
            try {
                java.time.YearMonth ym = java.time.YearMonth.parse(month);
                end = month + "-" + ym.lengthOfMonth() + " 23:59:59";
            } catch (Exception e) {
                start = null; end = null;
            }
        }

        // 调用 Repository 查出符合条件的所有数据
        List<Bill> allBills = repository.findBillsWithConditions(start, end, category, keyword);


        // 在内存中进行安全的分页截取

        int total = allBills.size();
        int fromIndex = (page - 1) * size;

        // 如果算出来的起始位置已经超过了总条数，说明前端查的页码太大了，直接返回空列表
        if (fromIndex >= total) {
            return ApiResponse.ok(new java.util.ArrayList<>());
        }

        // 计算结束位置，使用 Math.min 防止 toIndex 越界
        int toIndex = Math.min(fromIndex + size, total);

        // 截取当前页的数据
        List<Bill> pagedBills = allBills.subList(fromIndex, toIndex);

        return ApiResponse.ok(pagedBills);
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