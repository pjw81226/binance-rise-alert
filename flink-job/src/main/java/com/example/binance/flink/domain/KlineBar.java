package com.example.binance.flink.domain;

public class KlineBar {
    public String symbol;
    public long endTime;
    public double open, close, high, low;
    public double volume;
    public boolean isFinal;
}