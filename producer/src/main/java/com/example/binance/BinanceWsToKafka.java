package com.example.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

public final class BinanceWsToKafka {
    private static final ObjectMapper M = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        String symbolsCsv = System.getenv().getOrDefault("SYMBOLS", "btcusdt,ethusdt");
        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:9094");
        String interval  = System.getenv().getOrDefault("KLINE_INTERVAL", "1m");

        KafkaTopics.ensureDefaultTopics(bootstrap);

        String[] syms = symbolsCsv.toLowerCase().replace(" ", "").split(",");
        StringBuilder streams = new StringBuilder();
        for (int i = 0; i < syms.length; i++) {
            String s = syms[i];
            if (i > 0) streams.append("/");
            streams.append(s).append("@kline_").append(interval)
                    .append("/").append(s).append("@bookTicker")
                    .append("/").append(s).append("@aggTrade");
        }
        String wsUrl = "wss://stream.binance.com:9443/stream?streams=" + streams;

        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.ACKS_CONFIG, "1");

        try (KafkaProducer<String,String> producer = new KafkaProducer<>(p)) {
            CountDownLatch latch = new CountDownLatch(1);
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

            client.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(wsUrl), new Listener() {
                        private final StringBuilder buffer = new StringBuilder();

                        @Override public void onOpen(WebSocket webSocket) {
                            System.out.println("WS opened: " + wsUrl);
                            webSocket.request(1);
                            Listener.super.onOpen(webSocket);
                        }

                        @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            // 들어오는 데이터 조각을 버퍼에 계속 추가합니다.
                            buffer.append(data);

                            // 메시지의 마지막 조각일 때만 아래 로직을 실행합니다.
                            if (last) {
                                String json = buffer.toString();
                                try {
                                    JsonNode root = M.readTree(json);
                                    String stream = root.path("stream").asText("");
                                    String topic;
                                    if (stream.contains("kline")) topic = "binance.raw.kline";
                                    else if (stream.contains("bookTicker")) topic = "binance.raw.bookticker";
                                    else if (stream.contains("aggTrade")) topic = "binance.raw.aggtrade";
                                    else topic = "binance.raw.misc";

                                    producer.send(new ProducerRecord<>(topic, null, json), (md, ex) -> {
                                        if (ex != null) ex.printStackTrace();
                                    });
                                } catch (Exception e) {
                                    // 파싱에 실패한 경우, 어떤 데이터였는지 로그를 남기면 디버깅에 좋습니다.
                                    System.err.println("Failed to parse JSON: " + json);
                                    e.printStackTrace();
                                }
                                // 다음 메시지를 위해 버퍼를 비웁니다.
                                buffer.setLength(0);
                            }

                            webSocket.request(1);
                            return null;
                        }
                        @Override public void onError(WebSocket webSocket, Throwable error) {
                            error.printStackTrace();
                            latch.countDown();
                        }
                        @Override public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                            System.out.println("WS closed: " + statusCode + ", reason=" + reason);
                            latch.countDown();
                            return null;
                        }
                    }).join();

            latch.await();
        }
    }
}
