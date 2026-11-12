package com.example.backend.ai;

/**
 * 自然语言查账解析出来的查询条件（不是答案）。
 * <p>
 * 前端拿到后按 action 决定调哪个接口：
 * stat → /api/stat/...，list → /api/bill。
 * 这样「统计」由数据库算，而不是让模型随便编一个数。
 *
 * @param action    动作：stat 统计 / list 明细 / unknown 听不懂
 * @param start     起始日期 yyyy-MM-dd
 * @param end       结束日期 yyyy-MM-dd，含当天
 * @param category  分类，null 表示全部
 * @param aggregate 聚合方式：sum / count / max / avg
 * @param keyword   商户或备注关键词
 */
public record QuerySpec(
        String action,
        String start,
        String end,
        String category,
        String aggregate,
        String keyword
) {
}