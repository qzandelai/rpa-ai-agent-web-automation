package com.rpaai.service;

public enum TaskPriority {
    LOW(1),
    NORMAL(5),
    HIGH(10),
    URGENT(20);

    private final int value;

    TaskPriority(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}