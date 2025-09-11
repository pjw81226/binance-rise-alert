package com.example.binance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// 정의되지 않은 필드는 무시
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {
    @JsonProperty("type") public String type;
    @JsonProperty("symbol") public String symbol;
    @JsonProperty("volume") public double volume;
    @JsonProperty("volAvg") public double volAvg;
    @JsonProperty("change") public double change;
    @JsonProperty("greens") public int greens;
    @JsonProperty("n") public int n;
}