#!/bin/bash

# ======================================================
# Binance Producer 실행 스크립트
# ======================================================
# 사용법:
# ./run_producer.sh [Pair1, Pair2,...]
# 예시: ./run_producer.sh btcusdt,ethusdt,solusdt
# ======================================================


KAFKA_BOOTSTRAP="localhost:9094"
KLINE_INTERVAL="1m"
PRODUCER_DIR="producer"


if [ -z "$1" ]; then
  SYMBOLS="btcusdt,ethusdt"
  echo "입력된 심볼이 없어 기본값으로 실행합니다: $SYMBOLS"
else
  SYMBOLS="$1"
fi

echo "Producer 를 시작합니다..."
echo " > Symbols: $SYMBOLS"
echo " > Kafka Bootstrap: $KAFKA_BOOTSTRAP"
echo " > Kline Interval: $KLINE_INTERVAL"
echo "------------------------------------------------------"


cd "$PRODUCER_DIR" || exit

SYMBOLS=$SYMBOLS \
KAFKA_BOOTSTRAP=$KAFKA_BOOTSTRAP \
KLINE_INTERVAL=$KLINE_INTERVAL \
./gradlew run