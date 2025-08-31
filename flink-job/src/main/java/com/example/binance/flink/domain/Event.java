package com.example.binance.flink.domain;

public class Event {
    public StreamType type;
    public String symbol;
    public long ts;

    // kline_1m payload
    public KlineBar kline;

    // AGG  payload
    public double aggQty;

    // BOOK payload
    public double bid;
    public double ask;
}