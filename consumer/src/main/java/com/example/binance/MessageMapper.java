package com.example.binance;


public class MessageMapper {

    public String toDiscordPayload(Message signal) {
        String title;
        String description;
        int color;

        if ("volume_spike".equals(signal.type)) {
            title = String.format("📈 거래량 급증! - %S", signal.symbol);
            description = String.format(
                    "**코인:** %s%n**시그널:** 거래량 스파이크%n**현재 거래량:** %.2f%n**평균 거래량:** %.2f",
                    signal.symbol.toUpperCase(), signal.volume, signal.volAvg
            );
            color = 16734208; // 주황색
        } else if ("upward_momentum".equals(signal.type)) {
            title = String.format("🚀 상승 모멘텀! - %S", signal.symbol);
            description = String.format(
                    "**코인:** %s%n**시그널:** 상승 모멘텀 감지%n**가격 변화율:** +%.2f%%%n**상승 캔들 수:** %d/%d",
                    signal.symbol.toUpperCase(), signal.change * 100, signal.greens, signal.n - 1
            );
            color = 3066993; // 초록색
        } else {
            return null;
        }

        String escapedDescription = description.replace("\n", "\\n");

        return String.format(
                "{\"embeds\":[{\"title\":\"%s\",\"description\":\"%s\",\"color\":%d}]}",
                title, escapedDescription, color
        );
    }
}