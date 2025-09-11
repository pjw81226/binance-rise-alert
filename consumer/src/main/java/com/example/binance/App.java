package com.example.binance;

public class App {
    public static void main(String[] args) {
        DiscordNotificationService notificationService = new DiscordNotificationService();
        KafkaSignalConsumer kafkaConsumer = new KafkaSignalConsumer(notificationService);

        kafkaConsumer.start();
    }
}
