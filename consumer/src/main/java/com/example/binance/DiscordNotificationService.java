package com.example.binance;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class DiscordNotificationService {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageMapper messageMapper = new MessageMapper();

    public void sendNotification(String kafkaMessage) {
        try {
            // kafka message -> message object
            Message signal = objectMapper.readValue(kafkaMessage, Message.class);

            // message -> discord payload
            String discordPayload = messageMapper.toDiscordPayload(signal);
            if (discordPayload == null)
                return;

            String webhookUrl = Config.DISCORD_WEBHOOK_URL;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(discordPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 300) {
                System.err.printf("⚠️ 디스코드 전송 실패 (상태 코드: %d): %s%n", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            System.err.println("❌ 알림 전송 중 에러 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
}