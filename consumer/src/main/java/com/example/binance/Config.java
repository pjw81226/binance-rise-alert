package com.example.binance;

public class Config {
    public static final String KAFKA_BROKER_URL = "localhost:9992";
    public static final String[] SIGNAL_TOPICS = {"binance.signal.volume_spike", "binance.signal.upward_momentum"};
    public static final String CONSUMER_GROUP_ID = "discord-alert-consumer-group";

    public static String getDiscordWebhookUrl() {
        String webhookUrl = System.getenv("DISCORD WEBHOOK URL");
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            throw new IllegalStateException("DISCORD WEBHOOK URL이 설정되지 않았습니다.");
        }
        return webhookUrl;
    }
}