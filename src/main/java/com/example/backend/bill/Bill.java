package com.example.backend.bill;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * 账单实体。
 * 时间字段用 String 存 "yyyy-MM-dd HH:mm:ss"：
 * 1) 前后端交换不需要额外日期格式配置；
 * 2) 这个格式的字典序等于时间序，可以直接按字符串比较、排序、筛选。
 */
@Entity
@Table(name = "bill")
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 金额，单位元 */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** 分类，必须是九个枚举之一 */
    @Column(nullable = false, length = 16)
    private String category;

    /** 商户名 */
    @Column(length = 64)
    private String merchant;

    /** 消费时间 yyyy-MM-dd HH:mm:ss */
    @Column(name = "occurred_at", nullable = false, length = 19)
    private String occurredAt;

    /** 备注 */
    @Column(length = 255)
    private String note;

    /** 入库时间 yyyy-MM-dd HH:mm:ss */
    @Column(name = "created_at", nullable = false, length = 19)
    private String createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }
    public String getOccurredAt() { return occurredAt; }
    public void setOccurredAt(String occurredAt) { this.occurredAt = occurredAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}