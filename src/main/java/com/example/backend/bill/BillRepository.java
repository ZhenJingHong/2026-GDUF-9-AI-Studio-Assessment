package com.example.backend.bill;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 账单数据访问。继承 JpaRepository 后，save / findAll / deleteById 等方法自动就有。
 */
public interface BillRepository extends JpaRepository<Bill, Long> {

    /** 按分类查询，时间倒序 */
    List<Bill> findByCategoryOrderByOccurredAtDesc(String category);

    /** 按时间范围查询（字符串比较，格式必须统一为 yyyy-MM-dd HH:mm:ss） */
    List<Bill> findByOccurredAtBetweenOrderByOccurredAtDesc(String start, String end);


    @Query("SELECT b FROM Bill b WHERE " +
            "(:start IS NULL OR b.occurredAt >= :start) AND " +
            "(:end IS NULL OR b.occurredAt <= :end) AND " +
            "(:category IS NULL OR b.category = :category) AND " +
            "(:keyword IS NULL OR b.merchant LIKE %:keyword% OR b.note LIKE %:keyword%) " +
            "ORDER BY b.occurredAt DESC")
    List<Bill> findBillsWithConditions(
            @Param("start") String start,
            @Param("end") String end,
            @Param("category") String category,
            @Param("keyword") String keyword
    );
}