package com.example.backend.common;

import java.util.Set;

public final class BillCategories {
    private static final Set<String> VALUES = Set.of(
            "餐饮", "交通", "购物", "娱乐", "居住", "医疗", "教育", "其他");

    private BillCategories() {
    }

    public static boolean contains(String category) {
        return category != null && VALUES.contains(category);
    }
}
