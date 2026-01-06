package com.example.binance;

public class Config {
    public static final String KAFKA_BROKER_URL = System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "localhost:9094");
    public static final String[] SIGNAL_TOPICS = {"binance.signal.volume_spike", "binance.signal.upward_momentum"};
    public static final String CONSUMER_GROUP_ID = "discord-alert-consumer-group";
    public static final String DISCORD_WEBHOOK_URL = requireEnv("DISCORD_WEBHOOK_URL");

    private static String requireEnv(String key) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Missing required env var: " + key);
        }
        return v;
    }
}