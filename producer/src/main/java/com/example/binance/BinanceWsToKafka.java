package com.example.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

public final class BinanceWsToKafka {
    private static final ObjectMapper M = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        //환경변수에서 설정값 읽기, 기본값 제공
        String symbolsCsv = System.getenv().getOrDefault("SYMBOLS", "btcusdt,ethusdt");
        String bootstrap = System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:9094");
        String interval  = System.getenv().getOrDefault("KLINE_INTERVAL", "1m");

        //토픽생성하고
        KafkaTopics.ensureDefaultTopics(bootstrap);

        //웹소켓 스트림 URL 구성
        String[] syms = symbolsCsv.toLowerCase().replace(" ", "").split(",");
        StringBuilder streams = new StringBuilder();
        for (int i = 0; i < syms.length; i++) {
            String s = syms[i];
            if (i > 0)
                streams.append("/");
            streams.append(s).append("@kline_").append(interval)
                    .append("/").append(s).append("@bookTicker")
                    .append("/").append(s).append("@aggTrade");
        }
        String wsUrl = "wss://stream.binance.com:9443/stream?streams=" + streams;

        //카프카 프로듀서 설정
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.ACKS_CONFIG, "1");

        try (KafkaProducer<String,String> producer = new KafkaProducer<>(p)) {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

            final long baseDelayMs = Long.parseLong(System.getenv().getOrDefault("WS_RECONNECT_BASE_MS", "1000"));
            final long maxDelayMs = Long.parseLong(System.getenv().getOrDefault("WS_RECONNECT_MAX_MS", "60000"));
            final AtomicBoolean running = new AtomicBoolean(true);
            final AtomicReference<WebSocket> wsRef = new AtomicReference<>();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                WebSocket ws = wsRef.get();
                if (ws != null) {
                    try {
                        ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown").toCompletableFuture().get(3, TimeUnit.SECONDS);
                    } catch (Exception ignored) {}
                }
                try {
                    producer.flush();
                } catch (Exception ignored) {}
            }));

            long delayMs = baseDelayMs;
            while (running.get()) {
                CountDownLatch latch = new CountDownLatch(1);

                try {
                    WebSocket ws = client.newWebSocketBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .buildAsync(URI.create(wsUrl), new Listener() {
                                private final StringBuilder buffer = new StringBuilder();

                                @Override public void onOpen(WebSocket webSocket) {
                                    System.out.println("WS opened: " + wsUrl);
                                    webSocket.request(1);
                                    Listener.super.onOpen(webSocket);
                                }

                                @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                                    //들어오는 데이터 파편을 버퍼에 추가
                                    buffer.append(data);

                                    //메시지의 마지막 파편일때만 아래 로직 수행
                                    if (last) {
                                        String json = buffer.toString();
                                        buffer.setLength(0); //다음 메세지를 위해 버퍼 초기화

                                        try {
                                            JsonNode root = M.readTree(json);
                                            String stream = root.path("stream").asText("");
                                            String topic;
                                            if (stream.contains("kline")) topic = "binance.raw.kline";
                                            else if (stream.contains("bookTicker")) topic = "binance.raw.bookticker";
                                            else if (stream.contains("aggTrade")) topic = "binance.raw.aggtrade";
                                            else {
                                                System.err.println("Unknown stream type: " + stream + " / payload=" + json);
                                                webSocket.request(1);
                                                return CompletableFuture.completedFuture(null);
                                            }

                                            String symbol = root.path("data").path("s").asText();
                                            producer.send(new ProducerRecord<>(topic, symbol, json), (md, ex) -> {
                                                if (ex != null) ex.printStackTrace();
                                            });
                                        } catch (Exception e) {
                                            System.err.println("Failed to parse JSON: " + json);
                                            e.printStackTrace();
                                        }
                                    }

                                    webSocket.request(1);
                                    return CompletableFuture.completedFuture(null);
                                }

                                @Override public void onError(WebSocket webSocket, Throwable error) {
                                    System.err.println("WS error: " + error.getMessage());
                                    error.printStackTrace();
                                    latch.countDown();
                                }

                                @Override public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                                    System.out.println("WS closed: " + statusCode + ", reason=" + reason);
                                    latch.countDown();
                                    return CompletableFuture.completedFuture(null);
                                }
                            }).join();

                    wsRef.set(ws);
                    delayMs = baseDelayMs; //연결되면 백오프 리셋

                    // 연결이 끊길 때까지 대기
                    latch.await();
                } catch (Exception e) {
                    System.err.println("WS connection failed: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    wsRef.set(null);
                }

                if (!running.get())
                    break;

                System.err.println("WS disconnected. Reconnecting in " + delayMs + "ms...");
                Thread.sleep(delayMs);
                delayMs = Math.min(maxDelayMs, delayMs * 2);
            }
        }
    }
}

