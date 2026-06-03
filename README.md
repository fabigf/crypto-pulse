# CryptoPulse Ecosystem

CryptoPulse es una simulacion distribuida de una plataforma de trading de criptomonedas construida con Java 21, Spring Boot, Kafka, PostgreSQL, MongoDB y React.

Hoy ya soporta:

* market data real desde Binance para varios pares
* ordenes `LIMIT` de compra y venta
* settlement de wallet tras ejecucion
* proyeccion CQRS de portfolio
* market board en vivo con ticks y velas internas
* vista separada de `Market` con velas `4h`, `1d`, `1w` y `1M`
* despliegue completo por Docker Compose

## Estado actual

A fecha de 2026-05-29 el repositorio tiene seis modulos activos:

* `api-gateway`
* `market-feeder-service`
* `wallet-service`
* `order-processor-service`
* `analytics-service`
* `cryptopulse-ui`

La Fase 7 esta completada y validada. Encima de esa base ya hay dos extensiones de producto reales:

* trading multi-par con `BUY` y `SELL`
* market analysis multi-timeframe

## Stack tecnico

* Java 21 + Spring Boot 3.5.x
* Spring Cloud Gateway
* Spring Kafka
* Spring Data JPA + PostgreSQL 16
* Spring Data MongoDB + MongoDB 7
* Spring WebSocket STOMP
* React + Vite + Tailwind CSS
* Nginx para servir la SPA dockerizada

## Arquitectura

La solucion sigue una separacion CQRS sencilla:

* command side: `wallet-service` y `order-processor-service`
* read side: `analytics-service`
* entrada unica cliente: `api-gateway`
* UI: `cryptopulse-ui`

## Estructura del monorepo

* `api-gateway`: entrada unica HTTP y WebSocket.
* `market-feeder-service`: consulta Binance y publica `market-prices` para varios pares.
* `wallet-service`: crea usuarios, reserva balances y liquida ejecuciones.
* `order-processor-service`: cruza ordenes pendientes con precios y publica `order-events`.
* `analytics-service`: mantiene portfolios de lectura, snapshots de mercado, velas historicas y STOMP.
* `cryptopulse-ui`: consola web con `Trading Desk` y vista `Market`.

## Variables de entorno

Puedes usar el fichero [.env.example](/D:/crypto-pulse-workspace/.env.example:1) como base.

Variables actuales:

* `BINANCE_BASE_URL`
* `MARKET_SYMBOLS`
* `ANALYTICS_ALLOWED_ORIGIN_PATTERNS`
* `MARKET_CHART_DEFAULT_LIMIT`

Ejemplo:

```env
BINANCE_BASE_URL=https://api.binance.com
MARKET_SYMBOLS=BTCUSDT,ETHUSDT,SOLUSDT,ADAUSDT
ANALYTICS_ALLOWED_ORIGIN_PATTERNS=http://localhost:*,http://127.0.0.1:*,http://host.docker.internal:*
MARKET_CHART_DEFAULT_LIMIT=120
```

## Puertos previstos

| Modulo | Puerto | Tipo |
| :--- | :---: | :--- |
| `cryptopulse-ui` | `5173` | UI React o Nginx |
| `api-gateway` | `8080` | REST + proxy WebSocket |
| `wallet-service` | `8081` | REST |
| `analytics-service` | `8082` | REST + STOMP |
| `market-feeder-service` | `8083` | Scheduler + Kafka |
| `order-processor-service` | `8084` | Kafka |
| `postgres-wallet` | `5432` | PostgreSQL |
| `mongodb-analytics` | `27017` | MongoDB |
| `kafka` | `9092` | Kafka |

## Arranque rapido

Con Docker:

```bash
docker compose up -d --build
```

Sin Docker, por modulos:

```bash
mvn -pl api-gateway spring-boot:run
mvn -pl wallet-service spring-boot:run
mvn -pl market-feeder-service spring-boot:run
mvn -pl order-processor-service spring-boot:run
mvn -pl analytics-service spring-boot:run
cd cryptopulse-ui
npm install
npm run dev
```

## Endpoints clave

* `POST /api/v1/wallet/users`
* `POST /api/v1/wallet/orders/reserve`
* `GET /api/v1/analytics/portfolios/{userId}`
* `GET /api/v1/analytics/markets`
* `GET /api/v1/analytics/market/{ticker}`
* `GET /api/v1/analytics/market/{ticker}/chart?timeframe=4h|1d|1w|1M`
* STOMP `/ws-market`
* STOMP `/topic/portfolio/{userId}`
* STOMP `/topic/market/{ticker}`

## Flujo principal

1. La UI crea usuario o envia una orden `LIMIT` por el gateway.
2. `wallet-service` reserva USD en `BUY` o el activo base en `SELL`.
3. `wallet-service` publica `BalanceReservedEvent`.
4. `order-processor-service` guarda la orden en memoria.
5. `market-feeder-service` publica ticks reales para todos los pares configurados.
6. Cuando el precio intersecta, `order-processor-service` publica `OrderExecutedEvent`.
7. `wallet-service` liquida la ejecucion y devuelve diferencia en compras mejoradas.
8. `analytics-service` actualiza portfolio y market snapshots.
9. La UI se refresca por REST inicial y STOMP en vivo.
10. La vista `Market` consulta velas historicas de Binance a traves de `analytics-service`.

## Validaciones realizadas en esta revision

* `mvn test` del reactor completo
* `npm.cmd run build` en `cryptopulse-ui`

## Notas importantes

* El matching engine sigue siendo in-memory.
* Las velas del `Trading Desk` nacen del stream interno `market-prices`.
* Las velas `4h`, `1d`, `1w` y `1M` de la vista `Market` salen de Binance via `analytics-service`.
* La UI ya no queda pegada a `market:error` por un corte transitorio: el stream pasa por estados `connecting`, `reconnecting` u `offline`.
* El probe de readiness del frontend usa una ruta funcional del gateway en vez de depender de CORS sobre `actuator`.
* El backend WebSocket acepta patrones de origen mas amplios para no romper al alternar entre `5173`, `4173` o contenedor local.
* En Docker, el frontend usa `npm install` en build por compatibilidad practica del entorno Alpine con este lockfile.
