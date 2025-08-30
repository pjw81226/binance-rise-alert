package com.example.binance;

import java.time.Duration;
import java.util.*;
import org.apache.kafka.clients.admin.*;

public final class KafkaTopics {
    private KafkaTopics() {}

    public static void ensureDefaultTopics(String bootstrapServers) throws Exception {
        Properties p = new Properties();
        p.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        try (AdminClient admin = AdminClient.create(p)) {
            NewTopic kline = new NewTopic("binance.raw.kline", 6, (short)1).configs(defaultRetention());
            NewTopic agg   = new NewTopic("binance.raw.aggtrade", 6, (short)1).configs(defaultRetention());
            NewTopic book  = new NewTopic("binance.raw.bookticker", 6, (short)1).configs(defaultRetention());

            NewTopic vSig  = new NewTopic("binance.signal.volume_spike", 6, (short)1).configs(signalRetention());
            NewTopic mSig  = new NewTopic("binance.signal.upward_momentum", 6, (short)1).configs(signalRetention());

            ensureTopics(admin, Arrays.asList(kline, agg, book, vSig, mSig));
        }
    }

    private static void ensureTopics(AdminClient admin, List<NewTopic> topics) throws Exception {
        Set<String> existing = admin.listTopics().names().get();
        List<NewTopic> toCreate = new ArrayList<>();
        for (NewTopic t : topics) if (!existing.contains(t.name())) toCreate.add(t);
        if (!toCreate.isEmpty()) admin.createTopics(toCreate).all().get();
    }

    private static Map<String,String> defaultRetention() {
        Map<String,String> m = new HashMap<>();
        m.put("cleanup.policy", "delete");
        m.put("retention.ms", String.valueOf(Duration.ofDays(7).toMillis()));
        return m;
    }

    private static Map<String,String> signalRetention() {
        Map<String,String> m = new HashMap<>();
        m.put("cleanup.policy", "delete");
        m.put("retention.ms", String.valueOf(Duration.ofDays(30).toMillis()));
        return m;
    }
}
