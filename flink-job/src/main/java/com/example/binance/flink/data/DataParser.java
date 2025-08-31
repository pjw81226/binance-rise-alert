package com.example.binance.flink.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DataParser {
    private static final ObjectMapper M = new ObjectMapper();

    public static Event parse(String json) {
        try {
            Event event = wrapKline(json);
            if (event != null) return event;
            event = parseAgg(json);
            if (event != null) return event;
            return parseBook(json);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static Event wrapKline(String json) throws Exception {
        JsonNode r = M.readTree(json);
        JsonNode d = r.path("data");

        if (!isMatchingStream(r, d, "kline")) return null;

        JsonNode k = d.path("k");
        KlineBar b = new KlineBar();
        b.symbol  = d.path("s").asText();
        b.endTime = k.path("T").asLong();
        b.open    = k.path("o").asDouble();
        b.close   = k.path("c").asDouble();
        b.high    = k.path("h").asDouble();
        b.low     = k.path("l").asDouble();
        b.volume  = k.path("v").asDouble();
        b.isFinal = k.path("x").asBoolean();

        Event ev = new Event();
        ev.type = StreamType.KLINE;
        ev.symbol = b.symbol;
        ev.ts = b.endTime;
        ev.kline = b;
        return ev;
    }

    private static Event parseAgg(String json) throws Exception {
        JsonNode r = M.readTree(json);
        JsonNode d = r.path("data");

        if (!isMatchingStream(r, d, "aggTrade")) return null;

        Event ev = new Event();
        ev.type = StreamType.AGG;
        ev.symbol = d.path("s").asText();
        ev.ts = d.path("T").asLong(d.path("E").asLong());
        ev.aggQty = d.path("q").asDouble(0.0);
        return ev;
    }

    private static Event parseBook(String json) throws Exception {
        JsonNode r = M.readTree(json);
        JsonNode d = r.path("data");

        if (!isMatchingStream(r, d, "bookTicker")) return null;

        Event ev = new Event();
        ev.type = StreamType.BOOK;
        ev.symbol = d.path("s").asText();
        ev.ts = d.path("E").asLong(System.currentTimeMillis());
        ev.bid = d.path("b").asDouble();
        ev.ask = d.path("a").asDouble();
        return ev;
    }

    private static boolean isMatchingStream(JsonNode root, JsonNode data, String streamName) {
        if (data.isMissingNode()) return false;
        String stream = root.path("stream").asText("");
        String e = data.path("e").asText("");
        return stream.contains(streamName) || streamName.equals(e);
    }
}
