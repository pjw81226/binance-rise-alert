Binance Websocket Data stream 이용
===========================

https://developers.binance.com/docs/binance-spot-api-docs/web-socket-streams

> 프로젝트에서 사용하는 스트림 데이터는
> 1. {symbol}@kline_1m (캔들/1분봉) – 돌파·거래량 배수 계산
> 2. {symbol}@bookTicker (최우선 호가/수량) – 스프레드·호가 불균형(OBI)·압축 감지
> 3. {symbol}@aggTrade (체결 집계) – 매수 체결 쏠림(buy ratio) 계산

<br> <br> <br> <br>




### Aggregate Trade Streams

--- 
Aggregate Trade Stream 은 단일 테이커 주문에 대해 집계된 거래 정보를 제공한다.   
업데이트 속도는 realtime

```json lines
{
  "e": "aggTrade",    // Event type : 이벤트 타입
  "E": 1672515782136, // Event time : 이벤트 발생 시간 (timestamp)
  "s": "BNBBTC",      // Symbol : 심볼
  "a": 12345,         // Aggregate trade ID : 집계된 거래 ID
  "p": "0.001",       // Price : 가격
  "q": "100",         // Quantity : 수량
  "f": 100,           // First trade ID : 첫 번째 거래 ID
  "l": 105,           // Last trade ID : 마지막 거래 ID
  "T": 1672515782136, // Trade time : 거래 시간 (timestamp)
  "m": true,          // 매수자가 Maker인지 Taker인지?
  "M": true           // Ignore 
}
```

<br> <br>

### Trade Streams

---
Trade Streams raw 거래 정보를 전달한다. 각 거래에는 unique한 매수자와 매도자가 있다.

업데이트 속도는 realtime

```json lines
{
  "e": "trade",       // 이벤트 타입
  "E": 1672515782136, // 이벤트 발생 시간 (timestamp)
  "s": "BNBBTC",      // 심볼
  "t": 12345,         // 거래 ID
  "p": "0.001",       // 가격 
  "q": "100",         // 수량
  "T": 1672515782136, // 거래 시간 (timestamp)
  "m": true,          // 매수자가 Maker인지 Taker인지?
  "M": true           // Ignore
}
```

<br> <br>

### Kline(Candlestick) Streams for UTC

---
호가창의 캔들스틱 데이터 제공.  
업데이트 속도는 1s 간격은 1000ms, 기타 간격의 경우 2000ms

Stream Name: {symbol}@kline_{interval}

Kline/Candlestick chart intervals(캔들스틱 간격) :
- 1s
- 1m
- 3m
- 5m
- 15m
- 30m
- 1h
- 2h
- 4h
- 6h
- 8h
- 12h
- 1d
- 3d
- 1w
- 1M

```json lines
{
  "e": "kline",         // 이벤트 타입
  "E": 1672515782136,   // 이벤트 시간
  "s": "BNBBTC",        // 거래 심볼
  "k": {
    "t": 1672515780000, // 캔들 시작 시간
    "T": 1672515839999, // 캔들 종료 시간
    "s": "BNBBTC",      // 거래 심볼
    "i": "1m",          // 캔들 간격 (예: 1분봉)
    "f": 100,           // 첫 번째 체결 ID
    "L": 200,           // 마지막 체결 ID
    "o": "0.0010",      // 시가 (Open)
    "c": "0.0020",      // 종가 (Close)
    "h": "0.0025",      // 고가 (High)
    "l": "0.0015",      // 저가 (Low)
    "v": "1000",        // 거래량 (기초 자산 기준, BTC/USDT 라면 BTC 기준 개수)
    "n": 100,           // 체결된 거래 개수
    "x": false,         // 이 캔들이 닫혔는지? (true면 확정, false면 진행 중)
    "q": "1.0000",      // 거래량 (상대 자산 기준, BTC/USDT 라면 USDT 기준 개수)
    "V": "500",         // 매수자 체결량 (Taker가 구매한 기초 자산 수량)
    "Q": "0.500",       // 매수자 체결량 (Taker가 구매한 상대 자산 수량(금액))
    "B": "123456"       // Ignore
  }
}
```

<br><br>

### Individual Symbol Ticker Streams

---

단일 종목에 대한 24시간 이동 평균 티커 통계. 특정 심볼의 24시간 거래 통계를 보여준다.

업데이트 속도: 1000ms

```json lines
{
  "e": "24hrTicker",  // Event type
  "E": 1672515782136, // Event time
  "s": "BNBBTC",      // Symbol
  "p": "0.0015",      // Price change
  "P": "250.00",      // Price change percent
  "w": "0.0018",      // Weighted average price
  "x": "0.0009",      // First trade(F)-1 price (first trade before the 24hr rolling window)
  "c": "0.0025",      // Last price
  "Q": "10",          // Last quantity
  "b": "0.0024",      // Best bid price
  "B": "10",          // Best bid quantity
  "a": "0.0026",      // Best ask price
  "A": "100",         // Best ask quantity
  "o": "0.0010",      // Open price
  "h": "0.0025",      // High price
  "l": "0.0010",      // Low price
  "v": "10000",       // Total traded base asset volume
  "q": "18",          // Total traded quote asset volume
  "O": 0,             // Statistics open time
  "C": 86400000,      // Statistics close time
  "F": 0,             // First trade ID
  "L": 18150,         // Last trade Id
  "n": 18151          // Total number of trades
}
```

<br><br>

### Individual Symbol Book Ticker Streams

---

지정된 종목에 대한 최고 매수 또는 매도 가격 또는 수량에 대한 업데이트를 가져온다.  
여러 개의 {symbol}@bookTicker 스트림을 하나의 연결로 가져올 수 있다.

Stream Name: {symbol}@bookTicker
업데이트 속도는 realtime

```json lines
{
  "u":400900217,     // order book updateId
  "s":"BNBUSDT",     // symbol
  "b":"25.35190000", // best bid price
  "B":"31.21000000", // best bid qty
  "a":"25.36520000", // best ask price
  "A":"40.66000000"  // best ask qty
}
```