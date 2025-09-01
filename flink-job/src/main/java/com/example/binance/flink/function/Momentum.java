package com.example.binance.flink.function;

import com.example.binance.flink.data.Event;
import com.example.binance.flink.data.KlineBar;
import com.example.binance.flink.data.StreamType;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

public class Momentum extends KeyedProcessFunction<String, Event, String> {
    private final int nMom;
    private final double pctMom;
    private final double maxSpread;

    private transient ValueState<double[]> closes;
    private transient ValueState<Integer> closeCount;
    private transient ValueState<Double> lastBid;
    private transient ValueState<Double> lastAsk;

    public Momentum(int nMom, double pctMom, double maxSpread) {
        this.nMom = nMom;
        this.pctMom = pctMom;
        this.maxSpread = maxSpread;
    }

    @Override
    public void open(Configuration parameters) {
        closes = getRuntimeContext().getState(new ValueStateDescriptor<>("closes", double[].class));
        closeCount = getRuntimeContext().getState(new ValueStateDescriptor<>("closeCount", Integer.class));
        lastBid = getRuntimeContext().getState(new ValueStateDescriptor<>("lastBid", Double.class));
        lastAsk = getRuntimeContext().getState(new ValueStateDescriptor<>("lastAsk", Double.class));
    }

    @Override
    public void processElement(Event ev, Context ctx, Collector<String> out) throws Exception {
        if (ev.type == StreamType.BOOK) {
            lastBid.update(ev.bid);
            lastAsk.update(ev.ask);
            return;
        }

        if (ev.type != StreamType.KLINE) return;

        KlineBar b = ev.kline;
        if (b == null || !b.isFinal) return;

        // 종가 데이터 누적
        double[] arr = closes.value(); Integer c = closeCount.value();
        if (arr == null) {
            arr = new double[nMom];
            c = 0;
        }
        if (c < nMom) {
            arr[c] = b.close;
            c++;
        }
        else {
            System.arraycopy(arr, 1, arr, 0, nMom - 1);
            arr[nMom - 1] = b.close;
        }
        closes.update(arr); closeCount.update(c);

        if (c < nMom)
            return;

        // 모멘텀 조건 계산
        double first = arr[0], last = arr[nMom - 1];
        double change = (last - first) / first;
        int green = 0;
        for (int i = 1; i < nMom; i++)
            if (arr[i] > arr[i - 1]) green++;

        // 스프레드 조건 계산
        Double bid = lastBid.value(), ask = lastAsk.value();
        double spreadRatio = Double.POSITIVE_INFINITY;
        boolean isSpreadOk = false;
        if (bid != null && ask != null && bid > 0 && ask > 0) {
            double mid = 0.5 * (bid + ask);
            spreadRatio = (ask - bid) / mid;
            isSpreadOk = spreadRatio <= maxSpread;
        }

        // 최종 시그널 조건 검사
        if (change >= pctMom && green >= Math.max(1, Math.round((nMom - 1) * 0.6)) && isSpreadOk) {
            String json = String.format(
                    "{\"type\":\"upward_momentum\",\"symbol\":\"%s\",\"endTime\":%d," +
                            "\"change\":%.6f,\"greens\":%d,\"n\":%d,\"spread\":%.6f}",
                    b.symbol, b.endTime, change, green, nMom, spreadRatio);
            out.collect(json);
        }
    }
}