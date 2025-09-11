package com.example.binance;


import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;

public class KafkaSignalConsumer {
    private final KafkaConsumer<String, String> consumer;
    private final DiscordNotificationService discordNotificationService;

    public KafkaSignalConsumer(DiscordNotificationService discordNotificationService) {
        this.discordNotificationService = discordNotificationService;

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, Config.KAFKA_BROKER_URL);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, Config.CONSUMER_GROUP_ID);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        this.consumer = new KafkaConsumer<>(props);
    }

    public void start() {
        consumer.subscribe(Arrays.asList(Config.SIGNAL_TOPICS));
        System.out.println("Discord Alert Consumer Start: waiting signals...");

        try {
            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                records.forEach(record -> {
                    System.out.printf("Accept Signal [Topic: %s]: %s%n", record.topic(), record.value());
                    discordNotificationService.sendNotification(record.value());
                });
            }
        } finally {
            consumer.close();
            System.out.println("Consumer Exit");
        }
    }
}