package com.example.binance.flink;

import com.example.binance.flink.data.DataParser;
import com.example.binance.flink.data.Event;
import com.example.binance.flink.factory.DataSinkFactory;
import com.example.binance.flink.factory.DataSourceFactory;
import com.example.binance.flink.function.Momentum;
import com.example.binance.flink.function.VolumeSpike;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import java.time.Duration;
import java.util.Objects;

public class BinanceJob {

    public static void main(String[] args) throws Exception {
        final String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "kafka:9092");
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Kafka 소스 생성
        KafkaSource<String> klineSrc = DataSourceFactory.createSource(bootstrap, "binance.raw.kline", "flink-signals-job-kline");
        KafkaSource<String> aggSrc = DataSourceFactory.createSource(bootstrap, "binance.raw.aggtrade", "flink-signals-job-agg");
        KafkaSource<String> bookSrc = DataSourceFactory.createSource(bootstrap, "binance.raw.bookticker", "flink-signals-job-book");

        // 각 소스를 Event 스트림으로 파싱
        SingleOutputStreamOperator<Event> klineEvents = env.fromSource(klineSrc, WatermarkStrategy.noWatermarks(), "kline-source")
                .map(DataParser::parse).filter(Objects::nonNull);
        SingleOutputStreamOperator<Event> aggEvents = env.fromSource(aggSrc, WatermarkStrategy.noWatermarks(), "agg-source")
                .map(DataParser::parse).filter(Objects::nonNull);
        SingleOutputStreamOperator<Event> bookEvents = env.fromSource(bookSrc, WatermarkStrategy.noWatermarks(), "book-source")
                .map(DataParser::parse).filter(Objects::nonNull);

        // 모든 Event 스트림을 하나로 통합, 워터마크 적용
        WatermarkStrategy<Event> watermarkStrategy = WatermarkStrategy
                .<Event>forBoundedOutOfOrderness(Duration.ofSeconds(3))
                .withTimestampAssigner((event, ts) -> event.ts);

        DataStream<Event> combinedEvents = klineEvents.union(aggEvents, bookEvents)
                .assignTimestampsAndWatermarks(watermarkStrategy);

        // 스트림을 각 시그널 로직으로 분기
        // VolumeSpike
        DataStream<String> volSignals = combinedEvents
                .keyBy(e -> e.symbol)
                .process(new VolumeSpike(0.2, 2.5))
                .name("volume-spike-processor");

        // Momentum
        DataStream<String> momSignals = combinedEvents
                .keyBy(e -> e.symbol)
                .process(new Momentum(5, 0.008, 0.001))
                .name("momentum-processor");

        // 결과를 Kafka 토픽으로 전송
        KafkaSink<String> volSink = DataSinkFactory.createSignalSink(bootstrap, "binance.signal.volume_spike");
        KafkaSink<String> momSink = DataSinkFactory.createSignalSink(bootstrap, "binance.signal.upward_momentum");

        volSignals.sinkTo(volSink).name("sink-volume-spike");
        momSignals.sinkTo(momSink).name("sink-up-momentum");

        env.execute("Binance Signals Job (Full Logic, Modularized)");
    }
}