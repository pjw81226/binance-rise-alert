package com.example.binance.flink.function;

import com.example.binance.flink.data.Event;
import com.example.binance.flink.data.KlineBar;
import com.example.binance.flink.data.StreamType;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

public class VolumeSpike extends KeyedProcessFunction<String, Event, String> {
    private final double alphaVol;
    private final double mulVol;

    private transient ValueState<Double> volAvg;
    private transient ValueState<Integer> tradeCount;
    private transient ValueState<Double> tradeAvg;

    public VolumeSpike(double alphaVol, double mulVol) {
        this.alphaVol = alphaVol;
        this.mulVol = mulVol;
    }

    @Override
    public void open(Configuration parameters) {
        volAvg = getRuntimeContext().getState(new ValueStateDescriptor<>("volAvg", Double.class));
        tradeCount = getRuntimeContext().getState(new ValueStateDescriptor<>("tradeCount", Integer.class));
        tradeAvg = getRuntimeContext().getState(new ValueStateDescriptor<>("tradeAvg", Double.class));
    }

    @Override
    public void processElement(Event ev, Context ctx, Collector<String> out) throws Exception {
        if (ev.type == StreamType.AGG) {
            Integer c = tradeCount.value();
            tradeCount.update(c == null ? 1 : (c + 1));
            return;
        }

        if (ev.type != StreamType.KLINE) return;

        KlineBar klineBar = ev.kline;
        if (klineBar == null || !klineBar.isFinal) return;

        // 거래량 EWMA(Exponentially Weighted Moving Average) 갱신
        Double vAvgPrev = volAvg.value();
        double v = klineBar.volume;
        double vAvgNew = (vAvgPrev == null) ? v : (alphaVol * v + (1 - alphaVol) * vAvgPrev);
        volAvg.update(vAvgNew);

        // 거래 건수 EWMA 갱신
        Integer tCnt = tradeCount.value();
        if (tCnt == null) tCnt = 0;
        Double tAvgPrev = tradeAvg.value();
        double tAvgNew = (tAvgPrev == null) ? tCnt : (0.2 * tCnt + 0.8 * tAvgPrev);
        tradeAvg.update(tAvgNew);

        // 시그널 조건 검사
        boolean volSpike = (vAvgPrev != null) && (v >= vAvgNew * mulVol);
        boolean tradeBurst = (tAvgPrev != null) && (tCnt >= tAvgNew * 1.8);

        if (volSpike && tradeBurst) {
            String json = String.format(
                    "{\"type\":\"volume_spike\",\"symbol\":\"%s\",\"endTime\":%d,\"volume\":%f," +
                            "\"volAvg\":%f,\"tradeCount\":%d,\"tradeAvg\":%f}",
                    klineBar.symbol, klineBar.endTime, v, vAvgNew, tCnt, tAvgNew);
            out.collect(json);
        }

        // 다음 봉을 위해 거래 건수 리셋
        tradeCount.update(0);
    }
}