import { startTransition, useEffect, useState } from "react";
import { apiClient, getApiBaseUrl, getWebSocketUrl, waitForGatewayReadiness } from "./api";
import { MarketCandleChart } from "./components/MarketCandleChart";
import { MarketView } from "./components/MarketView";
import { useMarketStream } from "./hooks/useMarketStream";
import { usePortfolioStream } from "./hooks/usePortfolioStream";

const fallbackMarketPairs = ["BTCUSDT", "ETHUSDT", "SOLUSDT", "ADAUSDT"];

const initialRegisterForm = {
  username: "",
  email: ""
};

const initialOrderForm = {
  targetTicker: "BTCUSDT",
  side: "BUY",
  targetPrice: "50000.00",
  quantity: "0.10"
};

function formatMoney(value) {
  const numericValue = Number(value ?? 0);
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    minimumFractionDigits: 2,
    maximumFractionDigits: 8
  }).format(numericValue);
}

function formatAsset(value) {
  const numericValue = Number(value ?? 0);
  return new Intl.NumberFormat("en-US", {
    minimumFractionDigits: 0,
    maximumFractionDigits: 8
  }).format(numericValue);
}

function formatDate(value) {
  if (!value) {
    return "Waiting for first event";
  }
  return new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeStyle: "medium"
  }).format(new Date(value));
}

function formatPercent(value) {
  const numericValue = Number(value ?? 0);
  return `${numericValue >= 0 ? "+" : ""}${numericValue.toFixed(2)}%`;
}

function getDeltaTone(value) {
  return Number(value ?? 0) >= 0 ? "text-emerald-200" : "text-rose-200";
}

function getMarketCardTone(isActive) {
  return isActive
    ? "border-amber-300/35 bg-amber-300/10 ring-1 ring-amber-200/30"
    : "border-white/10 bg-white/5 hover:border-sky-300/25 hover:bg-sky-300/5";
}

function getConnectionTone(status) {
  switch (status) {
    case "live":
      return "bg-emerald-400/15 text-emerald-200 ring-emerald-400/40";
    case "connecting":
      return "bg-amber-400/15 text-amber-100 ring-amber-300/40";
    case "reconnecting":
      return "bg-sky-400/15 text-sky-100 ring-sky-300/40";
    case "offline":
      return "bg-rose-400/15 text-rose-100 ring-rose-400/40";
    case "error":
      return "bg-rose-400/15 text-rose-100 ring-rose-400/40";
    default:
      return "bg-white/10 text-stone-200 ring-white/20";
  }
}

function getChartStatusTone(status) {
  switch (status) {
    case "ready":
      return getConnectionTone("live");
    case "loading":
      return getConnectionTone("connecting");
    case "error":
      return getConnectionTone("offline");
    default:
      return getConnectionTone(status);
  }
}

function getGatewayStatusTone(status) {
  switch (status) {
    case "ready":
      return getConnectionTone("live");
    case "checking":
      return getConnectionTone("connecting");
    default:
      return getConnectionTone("offline");
  }
}

function getViewTone(isActive) {
  return isActive
    ? "bg-amber-300 text-stone-950"
    : "border border-white/10 bg-stone-950/70 text-white hover:border-amber-300/30";
}

function formatPairLabel(ticker) {
  if (!ticker) {
    return "Unknown pair";
  }
  if (ticker.endsWith("USDT") && ticker.length > 4) {
    return `${ticker.slice(0, -4)}/USDT`;
  }
  return ticker;
}

function getLimitTriggerLabel(side, ticker, targetPrice) {
  const pairLabel = formatPairLabel(ticker);
  if (side === "SELL") {
    return `${pairLabel} executes when the market trades at ${formatMoney(targetPrice)} or above.`;
  }
  return `${pairLabel} executes when the market trades at ${formatMoney(targetPrice)} or below.`;
}

function App() {
  const [activeView, setActiveView] = useState("desk");
  const [registerForm, setRegisterForm] = useState(initialRegisterForm);
  const [userIdInput, setUserIdInput] = useState("");
  const [orderForm, setOrderForm] = useState(initialOrderForm);
  const [activeUserId, setActiveUserId] = useState("");
  const [marketPairs, setMarketPairs] = useState(() =>
    fallbackMarketPairs.map((ticker) => ({ ticker, live: false }))
  );
  const [selectedTicker, setSelectedTicker] = useState(initialOrderForm.targetTicker);
  const [selectedTimeframe, setSelectedTimeframe] = useState("1d");
  const [portfolio, setPortfolio] = useState(null);
  const [walletSnapshot, setWalletSnapshot] = useState(null);
  const [marketSnapshot, setMarketSnapshot] = useState(null);
  const [marketChart, setMarketChart] = useState(null);
  const [gatewayStatus, setGatewayStatus] = useState("checking");
  const [gatewayReady, setGatewayReady] = useState(false);
  const [connectionStatus, setConnectionStatus] = useState("idle");
  const [marketStatus, setMarketStatus] = useState("idle");
  const [marketChartStatus, setMarketChartStatus] = useState("idle");
  const [feedback, setFeedback] = useState("");
  const [lastReservation, setLastReservation] = useState(null);
  const [busyAction, setBusyAction] = useState(null);
  const isBusy = busyAction !== null;

  async function loadPortfolio(userId) {
    const response = await apiClient.get(`/api/v1/analytics/portfolios/${userId}`);
    startTransition(() => setPortfolio(response.data));
  }

  async function loadWalletSnapshot(userId) {
    const response = await apiClient.get(`/api/v1/wallet/users/${userId}/snapshot`);
    startTransition(() => setWalletSnapshot(response.data));
  }

  async function loadMarketUniverse() {
    const response = await apiClient.get("/api/v1/analytics/markets");
    startTransition(() => setMarketPairs(response.data));
    if (response.data?.length) {
      setSelectedTicker((currentTicker) => currentTicker || response.data[0].ticker);
    }
  }

  async function loadMarketSnapshot(ticker) {
    const response = await apiClient.get(`/api/v1/analytics/market/${ticker}`);
    startTransition(() => setMarketSnapshot(response.data));
  }

  async function loadMarketChart(ticker, timeframe) {
    const response = await apiClient.get(`/api/v1/analytics/market/${ticker}/chart`, {
      params: {
        timeframe
      }
    });
    startTransition(() => setMarketChart(response.data));
  }

  useEffect(() => {
    let isActive = true;
    let retryTimer = null;

    const probeGateway = async () => {
      const ready = await waitForGatewayReadiness({ attempts: 1, delayMs: 0 });
      if (!isActive) {
        return;
      }

      setGatewayReady(ready);
      setGatewayStatus(ready ? "ready" : "checking");

      if (!ready) {
        retryTimer = window.setTimeout(probeGateway, 2000);
      }
    };

    probeGateway();

    return () => {
      isActive = false;
      window.clearTimeout(retryTimer);
    };
  }, []);

  useEffect(() => {
    let isActive = true;
    let retryTimer = null;

    if (!gatewayReady) {
      return undefined;
    }

    const syncMarketUniverse = async () => {
      try {
        await loadMarketUniverse();
      } catch {
        if (!isActive) {
          return;
        }

        setMarketPairs(fallbackMarketPairs.map((ticker) => ({ ticker, live: false })));
        setFeedback("Market list is using fallback pairs until analytics publishes live snapshots.");
        retryTimer = window.setTimeout(syncMarketUniverse, 2500);
      }
    };

    syncMarketUniverse();

    return () => {
      isActive = false;
      window.clearTimeout(retryTimer);
    };
  }, [gatewayReady]);

  useEffect(() => {
    let isActive = true;
    let retryTimer = null;

    if (!gatewayReady) {
      return undefined;
    }

    if (!selectedTicker) {
      setMarketSnapshot(null);
      setMarketChart(null);
      setMarketChartStatus("idle");
      return undefined;
    }

    setOrderForm((current) => ({
      ...current,
      targetTicker: selectedTicker
    }));

    const syncMarketSnapshot = async () => {
      try {
        await loadMarketSnapshot(selectedTicker);
      } catch {
        if (!isActive) {
          return;
        }

        setFeedback(`Market panel is waiting for the first analytics snapshot for ${selectedTicker}.`);
        setMarketSnapshot(null);
        retryTimer = window.setTimeout(syncMarketSnapshot, 2500);
      }
    };

    syncMarketSnapshot();

    return () => {
      isActive = false;
      window.clearTimeout(retryTimer);
    };
  }, [gatewayReady, selectedTicker]);

  useEffect(() => {
    let isActive = true;
    let retryTimer = null;

    if (!gatewayReady) {
      setMarketChartStatus("loading");
      return undefined;
    }

    if (!selectedTicker) {
      setMarketChart(null);
      setMarketChartStatus("idle");
      return undefined;
    }

    const syncMarketChart = async () => {
      setMarketChartStatus("loading");

      try {
        await loadMarketChart(selectedTicker, selectedTimeframe);
        if (!isActive) {
          return;
        }

        setMarketChartStatus("ready");
      } catch {
        if (!isActive) {
          return;
        }

        setMarketChartStatus("error");
        setMarketChart(null);
        setFeedback(`Historical candles for ${selectedTicker} (${selectedTimeframe}) are not available yet.`);
        retryTimer = window.setTimeout(syncMarketChart, 3000);
      }
    };

    syncMarketChart();

    return () => {
      isActive = false;
      window.clearTimeout(retryTimer);
    };
  }, [gatewayReady, selectedTicker, selectedTimeframe]);

  useEffect(() => {
    let isActive = true;
    let retryTimer = null;

    if (!gatewayReady) {
      return undefined;
    }

    if (!activeUserId) {
      setPortfolio(null);
      setWalletSnapshot(null);
      return undefined;
    }

    setPortfolio(null);
    setWalletSnapshot(null);

    const syncPortfolio = async () => {
      try {
        await loadPortfolio(activeUserId);
      } catch (error) {
        if (!isActive) {
          return;
        }

        if (error.response?.status === 404) {
          setFeedback("The user exists, but the analytics projection is still syncing. Retrying...");
        } else {
          setFeedback("We could not load the portfolio yet. Check the analytics service.");
        }
        retryTimer = window.setTimeout(syncPortfolio, 2500);
      }
    };

    syncPortfolio();

    return () => {
      isActive = false;
      window.clearTimeout(retryTimer);
    };
  }, [activeUserId, gatewayReady]);

  useEffect(() => {
    let isActive = true;
    let refreshTimer = null;

    if (!gatewayReady) {
      return undefined;
    }

    if (!activeUserId) {
      setWalletSnapshot(null);
      return undefined;
    }

    const syncWalletSnapshot = async () => {
      try {
        await loadWalletSnapshot(activeUserId);
      } catch (error) {
        if (!isActive) {
          return;
        }
        setFeedback(error.response?.data?.message ?? "We could not load the wallet snapshot yet.");
      } finally {
        if (isActive) {
          refreshTimer = window.setTimeout(syncWalletSnapshot, 5000);
        }
      }
    };

    syncWalletSnapshot();

    return () => {
      isActive = false;
      window.clearTimeout(refreshTimer);
    };
  }, [activeUserId, gatewayReady]);

  usePortfolioStream({
    enabled: gatewayReady,
    userId: activeUserId,
    onPortfolio: (nextPortfolio) => {
      startTransition(() => setPortfolio(nextPortfolio));
      if (activeUserId) {
        void loadWalletSnapshot(activeUserId);
      }
      setFeedback("Portfolio updated from live stream.");
    },
    onStatus: setConnectionStatus
  });

  useMarketStream({
    enabled: gatewayReady,
    ticker: selectedTicker,
    onSnapshot: (nextSnapshot) => {
      startTransition(() => setMarketSnapshot(nextSnapshot));
      startTransition(() => setMarketPairs((currentMarkets) => currentMarkets.map((market) => (
        market.ticker === nextSnapshot.ticker
          ? {
            ...market,
            ticker: nextSnapshot.ticker,
            latestPrice: nextSnapshot.latestPrice,
            percentageChange: nextSnapshot.percentageChange,
            updatedAt: nextSnapshot.updatedAt,
            live: true
          }
          : market
      ))));
    },
    onStatus: setMarketStatus
  });

  async function handleRegister(event) {
    event.preventDefault();
    setBusyAction("register");
    setFeedback("");

    try {
      const response = await apiClient.post("/api/v1/wallet/users", registerForm);
      const nextUserId = String(response.data.userId);
      setActiveUserId(nextUserId);
      setUserIdInput(nextUserId);
      setRegisterForm(initialRegisterForm);
      setLastReservation(null);
      await loadWalletSnapshot(nextUserId);
      setFeedback(`User created. Live desk attached to user ${nextUserId}. Waiting for analytics to project the portfolio...`);
    } catch (error) {
      setFeedback(error.response?.data?.message ?? "User registration failed.");
    } finally {
      setBusyAction(null);
    }
  }

  async function handleAttach(event) {
    event.preventDefault();
    if (!userIdInput.trim()) {
      setFeedback("Enter a user id before attaching the live dashboard.");
      return;
    }

    setBusyAction("attach");
    setFeedback("");

    try {
      const nextUserId = userIdInput.trim();
      await apiClient.get(`/api/v1/wallet/users/${nextUserId}`);
      setPortfolio(null);
      setWalletSnapshot(null);
      setLastReservation(null);
      setActiveUserId(nextUserId);
      await loadWalletSnapshot(nextUserId);
      setFeedback(`Listening to portfolio ${nextUserId}.`);
    } catch (error) {
      setFeedback(error.response?.data?.message ?? "We could not find that user.");
    } finally {
      setBusyAction(null);
    }
  }

  async function handleReserveOrder(event) {
    event.preventDefault();
    if (!activeUserId) {
      setFeedback("Create or attach a user before sending an order.");
      return;
    }

    setBusyAction("reserve");
    setFeedback("");

    try {
      const payload = {
        userId: Number(activeUserId),
        targetTicker: orderForm.targetTicker,
        side: orderForm.side,
        orderType: "LIMIT",
        targetPrice: orderForm.targetPrice,
        quantity: orderForm.quantity
      };
      const response = await apiClient.post("/api/v1/wallet/orders/reserve", payload);
      setLastReservation(response.data);
      await loadWalletSnapshot(activeUserId);
      await loadPortfolio(activeUserId).catch(() => {});
      setFeedback(`${orderForm.side} order reserved on ${orderForm.targetTicker}. ${getLimitTriggerLabel(orderForm.side, orderForm.targetTicker, orderForm.targetPrice)}`);
    } catch (error) {
      await loadWalletSnapshot(activeUserId).catch(() => {});
      setFeedback(error.response?.data?.message ?? "Order reservation failed. Review the pending orders below.");
    } finally {
      setBusyAction(null);
    }
  }

  async function handleCancelOrder(orderId) {
    if (!activeUserId) {
      return;
    }

    setBusyAction(`cancel-${orderId}`);
    setFeedback("");

    try {
      await apiClient.post(`/api/v1/wallet/users/${activeUserId}/orders/${orderId}/cancel`);
      if (lastReservation?.eventId === orderId) {
        setLastReservation(null);
      }
      await loadWalletSnapshot(activeUserId);
      await loadPortfolio(activeUserId).catch(() => {});
      setFeedback(`Pending order ${orderId} cancelled. Reserved funds are available again.`);
    } catch (error) {
      setFeedback(error.response?.data?.message ?? "We could not cancel that pending order.");
    } finally {
      setBusyAction(null);
    }
  }

  function applyLivePrice() {
    if (!marketSnapshot?.latestPrice) {
      return;
    }

    setOrderForm((current) => ({
      ...current,
      targetPrice: Number(marketSnapshot.latestPrice).toFixed(2)
    }));
  }

  const assetEntries = Object.entries(walletSnapshot?.assetBalances ?? {});
  const pendingOrders = walletSnapshot?.pendingOrders ?? [];
  const selectedAssetCode = orderForm.targetTicker.endsWith("USDT")
    ? orderForm.targetTicker.slice(0, -4)
    : orderForm.targetTicker;
  const recentTicks = [...(marketSnapshot?.recentTicks ?? [])].reverse().slice(0, 8);

  return (
    <div className="min-h-screen bg-[radial-gradient(circle_at_top,rgba(251,191,36,0.22),transparent_32%),radial-gradient(circle_at_bottom_right,rgba(14,165,233,0.20),transparent_30%),linear-gradient(160deg,#0c0a09_0%,#1c1917_45%,#292524_100%)] text-stone-100">
      <div className="mx-auto flex min-h-screen max-w-7xl flex-col px-5 py-8 sm:px-8">
        <header className="grid gap-6 rounded-[2rem] border border-white/10 bg-black/20 p-6 shadow-[0_30px_80px_rgba(0,0,0,0.35)] backdrop-blur xl:grid-cols-[1.2fr_0.8fr]">
          <div className="space-y-5">
            <div className="flex flex-wrap items-center justify-between gap-4">
              <p className="text-xs font-semibold uppercase tracking-[0.45em] text-amber-200/75">
                CryptoPulse Live Desk
              </p>
              <div className="flex flex-wrap gap-3">
                <button
                  className={`rounded-full px-4 py-2 text-sm font-semibold transition ${getViewTone(activeView === "desk")}`}
                  type="button"
                  onClick={() => setActiveView("desk")}
                >
                  Trading Desk
                </button>
                <button
                  className={`rounded-full px-4 py-2 text-sm font-semibold transition ${getViewTone(activeView === "market")}`}
                  type="button"
                  onClick={() => setActiveView("market")}
                >
                  Market
                </button>
              </div>
            </div>
            <h1 className="font-display max-w-2xl text-4xl leading-tight text-stone-50 sm:text-5xl">
              Trade the configured pairs and inspect live plus historical market structure from one control room.
            </h1>
            <p className="max-w-2xl text-sm leading-7 text-stone-300 sm:text-base">
              The desk keeps the end-to-end reservation and portfolio flow, while the new Market view focuses on
              multi-timeframe candles powered by Binance and streamed read models.
            </p>
            <div className="flex flex-wrap gap-3 text-xs">
              <span className={`rounded-full px-4 py-2 ring-1 ${getConnectionTone(connectionStatus)}`}>
                Portfolio: {connectionStatus}
              </span>
              <span className={`rounded-full px-4 py-2 ring-1 ${getConnectionTone(marketStatus)}`}>
                Market stream: {marketStatus}
              </span>
              <span className={`rounded-full px-4 py-2 ring-1 ${getChartStatusTone(marketChartStatus)}`}>
                Market history: {marketChartStatus}
              </span>
              <span className={`rounded-full px-4 py-2 ring-1 ${getGatewayStatusTone(gatewayStatus)}`}>
                Gateway: {gatewayStatus}
              </span>
              <span className="rounded-full bg-white/10 px-4 py-2 ring-1 ring-white/15">
                API: {getApiBaseUrl()}
              </span>
              <span className="rounded-full bg-white/10 px-4 py-2 ring-1 ring-white/15">
                STOMP: {getWebSocketUrl()}
              </span>
            </div>
          </div>

          <div className="grid gap-4 rounded-[1.75rem] border border-amber-200/10 bg-amber-50/5 p-5">
            <div className="grid gap-1">
              <span className="text-xs uppercase tracking-[0.35em] text-amber-200/75">Active user</span>
              <strong className="text-2xl text-white">{activeUserId || "No user attached yet"}</strong>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="rounded-2xl bg-black/20 p-4 ring-1 ring-white/10">
                <p className="text-xs uppercase tracking-[0.3em] text-stone-400">USD balance</p>
                <p className="mt-2 text-2xl font-semibold text-emerald-200">
                  {formatMoney(walletSnapshot?.usdBalance)}
                </p>
              </div>
              <div className="rounded-2xl bg-black/20 p-4 ring-1 ring-white/10">
                <p className="text-xs uppercase tracking-[0.3em] text-stone-400">{selectedAssetCode} balance</p>
                <p className="mt-2 text-2xl font-semibold text-sky-200">
                  {formatAsset(walletSnapshot?.assetBalances?.[selectedAssetCode])}
                </p>
              </div>
            </div>
            <p className="text-sm text-stone-300">
              {feedback || "System ready. Start by registering a demo trader or jump to the Market view."}
            </p>
          </div>
        </header>

        <main className="mt-8 grid gap-6">
          {activeView === "market" ? (
            <MarketView
              chartStatus={marketChartStatus}
              formatDate={formatDate}
              formatMoney={formatMoney}
              formatPairLabel={formatPairLabel}
              formatPercent={formatPercent}
              getConnectionTone={getConnectionTone}
              getDeltaTone={getDeltaTone}
              marketChart={marketChart}
              marketPairs={marketPairs}
              marketSnapshot={marketSnapshot}
              marketStatus={marketStatus}
              selectedTicker={selectedTicker}
              selectedTimeframe={selectedTimeframe}
              setSelectedTicker={setSelectedTicker}
              setSelectedTimeframe={setSelectedTimeframe}
            />
          ) : (
            <>
              <section className="rounded-[2rem] border border-sky-200/10 bg-black/25 p-6 backdrop-blur">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <p className="text-xs font-semibold uppercase tracking-[0.4em] text-sky-200/70">
                      Market board
                    </p>
                    <h2 className="font-display mt-3 text-3xl text-white">
                      {formatPairLabel(selectedTicker)} live terminal
                    </h2>
                    <p className="mt-2 max-w-2xl text-sm text-stone-400">
                      Prices are pulled from Binance, published into `market-prices`, and projected by
                      `analytics-service` into per-pair live candles and ticks.
                    </p>
                  </div>
                  <div className="grid gap-3 lg:grid-cols-[1fr_repeat(3,minmax(0,1fr))]">
                    <label className="rounded-2xl bg-stone-950/80 p-4 ring-1 ring-white/10">
                      <p className="text-xs uppercase tracking-[0.3em] text-stone-500">Pair</p>
                      <select
                        className="mt-2 w-full bg-transparent text-lg font-semibold text-white outline-none"
                        value={selectedTicker}
                        onChange={(event) => setSelectedTicker(event.target.value)}
                      >
                        {marketPairs.map((market) => (
                          <option key={market.ticker} className="bg-stone-950 text-white" value={market.ticker}>
                            {formatPairLabel(market.ticker)}
                          </option>
                        ))}
                      </select>
                    </label>
                    <div className="rounded-2xl bg-stone-950/80 p-4 ring-1 ring-white/10">
                      <p className="text-xs uppercase tracking-[0.3em] text-stone-500">Last price</p>
                      <p className="mt-2 text-2xl font-semibold text-white">
                        {formatMoney(marketSnapshot?.latestPrice)}
                      </p>
                    </div>
                    <div className="rounded-2xl bg-stone-950/80 p-4 ring-1 ring-white/10">
                      <p className="text-xs uppercase tracking-[0.3em] text-stone-500">Session change</p>
                      <p className={`mt-2 text-2xl font-semibold ${getDeltaTone(marketSnapshot?.percentageChange)}`}>
                        {formatPercent(marketSnapshot?.percentageChange)}
                      </p>
                    </div>
                    <div className="rounded-2xl bg-stone-950/80 p-4 ring-1 ring-white/10">
                      <p className="text-xs uppercase tracking-[0.3em] text-stone-500">Absolute move</p>
                      <p className={`mt-2 text-2xl font-semibold ${getDeltaTone(marketSnapshot?.absoluteChange)}`}>
                        {formatMoney(marketSnapshot?.absoluteChange)}
                      </p>
                    </div>
                  </div>
                </div>

                <div className="mt-4 flex flex-wrap gap-3">
                  <button
                    className="rounded-full bg-amber-300 px-4 py-2 text-sm font-semibold text-stone-950 transition hover:bg-amber-200"
                    type="button"
                    onClick={() => setActiveView("market")}
                  >
                    Open market analysis
                  </button>
                </div>

                <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                  {marketPairs.map((market) => (
                    <button
                      key={market.ticker}
                      className={`rounded-[1.5rem] border p-4 text-left transition ${getMarketCardTone(market.ticker === selectedTicker)}`}
                      type="button"
                      onClick={() => setSelectedTicker(market.ticker)}
                    >
                      <p className="text-xs uppercase tracking-[0.35em] text-stone-500">{formatPairLabel(market.ticker)}</p>
                      <p className="mt-3 text-2xl font-semibold text-white">{formatMoney(market.latestPrice)}</p>
                      <div className="mt-3 flex items-center justify-between text-xs">
                        <span className={getDeltaTone(market.percentageChange)}>
                          {market.live ? formatPercent(market.percentageChange) : "Waiting"}
                        </span>
                        <span className="text-stone-400">
                          {market.updatedAt ? formatDate(market.updatedAt) : "No ticks yet"}
                        </span>
                      </div>
                    </button>
                  ))}
                </div>

                <div className="mt-6 grid gap-6 xl:grid-cols-[1.1fr_0.55fr]">
                  <div className="grid gap-4">
                    <MarketCandleChart
                      candles={marketSnapshot?.candles ?? []}
                      timeframe="1m"
                      title={`${formatPairLabel(selectedTicker)} live internal candles`}
                    />
                    <div className="grid gap-4 sm:grid-cols-4">
                      <div className="rounded-[1.5rem] border border-white/10 bg-white/5 p-4">
                        <p className="text-xs uppercase tracking-[0.3em] text-stone-500">Open</p>
                        <p className="mt-2 text-xl font-semibold text-white">
                          {formatMoney(marketSnapshot?.sessionOpenPrice)}
                        </p>
                      </div>
                      <div className="rounded-[1.5rem] border border-white/10 bg-white/5 p-4">
                        <p className="text-xs uppercase tracking-[0.3em] text-stone-500">High</p>
                        <p className="mt-2 text-xl font-semibold text-emerald-200">
                          {formatMoney(marketSnapshot?.candles?.at(-1)?.high)}
                        </p>
                      </div>
                      <div className="rounded-[1.5rem] border border-white/10 bg-white/5 p-4">
                        <p className="text-xs uppercase tracking-[0.3em] text-stone-500">Low</p>
                        <p className="mt-2 text-xl font-semibold text-rose-200">
                          {formatMoney(marketSnapshot?.candles?.at(-1)?.low)}
                        </p>
                      </div>
                      <div className="rounded-[1.5rem] border border-white/10 bg-white/5 p-4">
                        <p className="text-xs uppercase tracking-[0.3em] text-stone-500">Updated</p>
                        <p className="mt-2 text-sm font-semibold text-stone-100">
                          {formatDate(marketSnapshot?.updatedAt)}
                        </p>
                      </div>
                    </div>
                  </div>

                  <div className="rounded-[1.75rem] border border-white/10 bg-stone-950/70 p-5">
                    <div className="flex items-center justify-between">
                      <div>
                        <h3 className="font-display text-2xl text-white">Recent ticks</h3>
                        <p className="text-sm text-stone-400">Newest market prints arriving through Kafka.</p>
                      </div>
                    </div>

                    <div className="mt-5 grid gap-3">
                      {recentTicks.length ? recentTicks.map((tick) => (
                        <div key={`${tick.timestamp}-${tick.price}`} className="flex items-center justify-between rounded-2xl border border-white/10 bg-white/5 px-4 py-3">
                          <div>
                            <p className="text-sm font-semibold text-white">{formatMoney(tick.price)}</p>
                            <p className="text-xs text-stone-400">{formatDate(tick.timestamp)}</p>
                          </div>
                          <span className="rounded-full bg-sky-300/12 px-3 py-1 text-xs uppercase tracking-[0.25em] text-sky-100 ring-1 ring-sky-200/20">
                            tick
                          </span>
                        </div>
                      )) : (
                        <div className="rounded-2xl border border-dashed border-white/15 bg-white/5 p-6 text-center text-sm text-stone-400">
                          Waiting for the first live ticks...
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              </section>

              <section className="grid gap-6 xl:grid-cols-[0.95fr_1.05fr]">
                <div className="grid gap-6">
                  <article className="rounded-[1.75rem] border border-white/10 bg-black/25 p-6 backdrop-blur">
                    <div className="mb-5 flex items-center justify-between">
                      <div>
                        <h2 className="font-display text-2xl text-white">
                          Register trader
                        </h2>
                        <p className="text-sm text-stone-400">Wallet service will create the account with a 10,000 USD demo balance.</p>
                      </div>
                    </div>
                    <form className="grid gap-4" onSubmit={handleRegister}>
                      <label className="grid gap-2 text-sm text-stone-300">
                        Username
                        <input
                          className="rounded-2xl border border-white/10 bg-stone-950/80 px-4 py-3 text-white outline-none transition focus:border-amber-300/40"
                          value={registerForm.username}
                          onChange={(event) => setRegisterForm((current) => ({ ...current, username: event.target.value }))}
                          placeholder="satoshi-lite"
                          required
                        />
                      </label>
                      <label className="grid gap-2 text-sm text-stone-300">
                        Email
                        <input
                          className="rounded-2xl border border-white/10 bg-stone-950/80 px-4 py-3 text-white outline-none transition focus:border-amber-300/40"
                          value={registerForm.email}
                          onChange={(event) => setRegisterForm((current) => ({ ...current, email: event.target.value }))}
                          placeholder="trader@cryptopulse.dev"
                          required
                          type="email"
                        />
                      </label>
                      <button
                        className="rounded-2xl bg-amber-300 px-4 py-3 font-semibold text-stone-950 transition hover:bg-amber-200 disabled:cursor-not-allowed disabled:bg-amber-300/60"
                        disabled={isBusy}
                        type="submit"
                      >
                        {busyAction === "register" ? "Creating user..." : "Create user"}
                      </button>
                    </form>
                  </article>

                  <article className="rounded-[1.75rem] border border-white/10 bg-black/25 p-6 backdrop-blur">
                    <h2 className="font-display text-2xl text-white">
                      Attach existing portfolio
                    </h2>
                    <p className="mt-2 text-sm text-stone-400">
                      Use this when you already have a numeric user id and want the dashboard to subscribe again.
                    </p>
                    <form className="mt-5 flex flex-col gap-4 sm:flex-row" onSubmit={handleAttach}>
                      <input
                        className="min-w-0 flex-1 rounded-2xl border border-white/10 bg-stone-950/80 px-4 py-3 text-white outline-none transition focus:border-sky-300/40"
                        disabled={isBusy}
                        value={userIdInput}
                        onChange={(event) => setUserIdInput(event.target.value)}
                        placeholder="42"
                      />
                      <button
                        className="rounded-2xl bg-sky-300 px-5 py-3 font-semibold text-stone-950 transition hover:bg-sky-200 disabled:cursor-not-allowed disabled:bg-sky-300/60"
                        disabled={isBusy}
                        type="submit"
                      >
                        {busyAction === "attach" ? "Checking user..." : "Attach stream"}
                      </button>
                    </form>
                  </article>

                  <article className="rounded-[1.75rem] border border-white/10 bg-black/25 p-6 backdrop-blur">
                    <h2 className="font-display text-2xl text-white">
                      Submit limit order
                    </h2>
                    <p className="mt-2 text-sm text-stone-400">
                      Buy orders reserve USD. Sell orders reserve the base asset balance for the selected pair before the matching engine executes.
                    </p>
                    <form className="mt-5 grid gap-4" onSubmit={handleReserveOrder}>
                      <div className="grid gap-4 sm:grid-cols-3">
                        <label className="grid gap-2 text-sm text-stone-300">
                          Pair
                          <select
                            className="rounded-2xl border border-white/10 bg-stone-950/80 px-4 py-3 text-white outline-none transition focus:border-amber-300/40"
                            value={orderForm.targetTicker}
                            onChange={(event) => {
                              setOrderForm((current) => ({ ...current, targetTicker: event.target.value }));
                              setSelectedTicker(event.target.value);
                            }}
                          >
                            {marketPairs.map((market) => (
                              <option key={market.ticker} className="bg-stone-950 text-white" value={market.ticker}>
                                {formatPairLabel(market.ticker)}
                              </option>
                            ))}
                          </select>
                        </label>
                        <label className="grid gap-2 text-sm text-stone-300">
                          Side
                          <div className="grid grid-cols-2 gap-2">
                            <button
                              className={`rounded-2xl px-4 py-3 font-semibold transition ${
                                orderForm.side === "BUY"
                                  ? "bg-emerald-300 text-stone-950"
                                  : "border border-white/10 bg-stone-950/80 text-white"
                              }`}
                              type="button"
                              onClick={() => setOrderForm((current) => ({ ...current, side: "BUY" }))}
                            >
                              Buy
                            </button>
                            <button
                              className={`rounded-2xl px-4 py-3 font-semibold transition ${
                                orderForm.side === "SELL"
                                  ? "bg-rose-300 text-stone-950"
                                  : "border border-white/10 bg-stone-950/80 text-white"
                              }`}
                              type="button"
                              onClick={() => setOrderForm((current) => ({ ...current, side: "SELL" }))}
                            >
                              Sell
                            </button>
                          </div>
                        </label>
                        <label className="grid gap-2 text-sm text-stone-300">
                          Limit price
                          <div className="flex gap-2">
                            <input
                              className="min-w-0 flex-1 rounded-2xl border border-white/10 bg-stone-950/80 px-4 py-3 text-white outline-none transition focus:border-amber-300/40"
                              value={orderForm.targetPrice}
                              onChange={(event) => setOrderForm((current) => ({ ...current, targetPrice: event.target.value }))}
                              min="0"
                              required
                              step="0.00000001"
                              type="number"
                            />
                            <button
                              className="rounded-2xl border border-amber-300/25 bg-amber-300/10 px-4 py-3 text-xs font-semibold uppercase tracking-[0.25em] text-amber-100 transition hover:bg-amber-300/20 disabled:cursor-not-allowed disabled:opacity-60"
                              disabled={!marketSnapshot?.latestPrice || isBusy}
                              onClick={applyLivePrice}
                              type="button"
                            >
                              Use live
                            </button>
                          </div>
                        </label>
                      </div>
                      <label className="grid gap-2 text-sm text-stone-300">
                        Quantity
                        <input
                          className="rounded-2xl border border-white/10 bg-stone-950/80 px-4 py-3 text-white outline-none transition focus:border-amber-300/40"
                          value={orderForm.quantity}
                          onChange={(event) => setOrderForm((current) => ({ ...current, quantity: event.target.value }))}
                          min="0"
                          required
                          step="0.00000001"
                          type="number"
                        />
                      </label>
                      <div className="rounded-2xl border border-white/10 bg-stone-950/60 p-4 text-sm text-stone-300">
                        <p>
                          Selected pair: <strong className="text-white">{formatPairLabel(orderForm.targetTicker)}</strong>
                        </p>
                        <p className="mt-2">
                          Reservation:{" "}
                          <strong className="text-white">
                            {orderForm.side === "BUY"
                              ? `${formatMoney(Number(orderForm.targetPrice || 0) * Number(orderForm.quantity || 0))} USD`
                              : `${formatAsset(orderForm.quantity)} ${selectedAssetCode}`}
                          </strong>
                        </p>
                        <p className="mt-2 text-xs text-stone-400">
                          Last market reference: {formatMoney(marketSnapshot?.latestPrice)}
                        </p>
                        <p className="mt-2 text-xs text-stone-400">
                          {getLimitTriggerLabel(orderForm.side, orderForm.targetTicker, orderForm.targetPrice)}
                        </p>
                      </div>
                      <button
                        className={`rounded-2xl px-4 py-3 font-semibold text-stone-950 transition disabled:cursor-not-allowed ${
                          orderForm.side === "BUY"
                            ? "bg-emerald-300 hover:bg-emerald-200 disabled:bg-emerald-300/60"
                            : "bg-rose-300 hover:bg-rose-200 disabled:bg-rose-300/60"
                        }`}
                        disabled={isBusy}
                        type="submit"
                      >
                        {busyAction === "reserve" ? "Submitting order..." : `Reserve ${orderForm.side.toLowerCase()} limit order`}
                      </button>
                    </form>
                    {lastReservation ? (
                      <div className="mt-5 rounded-2xl border border-emerald-300/20 bg-emerald-400/10 p-4 text-sm text-emerald-100">
                        <p className="font-semibold">Last reservation</p>
                        <p>Event id: {lastReservation.eventId}</p>
                        <p>Pair: {lastReservation.targetTicker}</p>
                        <p>Side: {lastReservation.side}</p>
                        <p>
                          Reserved: {lastReservation.currency === "USD"
                            ? formatMoney(lastReservation.amountReserved)
                            : `${formatAsset(lastReservation.amountReserved)} ${lastReservation.currency}`}
                        </p>
                        <p>Status: {lastReservation.status}</p>
                      </div>
                    ) : null}
                  </article>
                </div>

                <div className="grid gap-6">
                  <article className="rounded-[1.75rem] border border-white/10 bg-black/25 p-6 backdrop-blur">
                    <div className="flex flex-wrap items-center justify-between gap-4">
                      <div>
                        <h2 className="font-display text-2xl text-white">
                          Wallet state
                        </h2>
                        <p className="text-sm text-stone-400">
                          Authoritative balances from wallet-service, including funds currently locked by pending limit orders.
                        </p>
                      </div>
                    </div>

                    <div className="mt-6 grid gap-4 md:grid-cols-3">
                      <div className="rounded-[1.5rem] border border-white/10 bg-stone-950/70 p-5">
                        <p className="text-xs uppercase tracking-[0.35em] text-stone-500">USD</p>
                        <p className="mt-3 text-3xl font-semibold text-emerald-200">{formatMoney(walletSnapshot?.usdBalance)}</p>
                      </div>
                      <div className="rounded-[1.5rem] border border-white/10 bg-stone-950/70 p-5">
                        <p className="text-xs uppercase tracking-[0.35em] text-stone-500">Tracked assets</p>
                        <p className="mt-3 text-3xl font-semibold text-sky-200">{assetEntries.length}</p>
                      </div>
                      <div className="rounded-[1.5rem] border border-white/10 bg-stone-950/70 p-5">
                        <p className="text-xs uppercase tracking-[0.35em] text-stone-500">Pending orders</p>
                        <p className="mt-3 text-3xl font-semibold text-amber-200">{pendingOrders.length}</p>
                      </div>
                    </div>

                    <div className="mt-6 grid gap-4 md:grid-cols-2">
                      {(assetEntries.length ? assetEntries : [["BTC", 0]]).map(([asset, amount]) => (
                        <div key={asset} className="rounded-[1.5rem] border border-white/10 bg-white/5 p-5">
                          <p className="text-xs uppercase tracking-[0.35em] text-stone-500">{asset}</p>
                          <p className="mt-3 text-2xl font-semibold text-white">{formatAsset(amount)}</p>
                        </div>
                      ))}
                    </div>
                  </article>

                  <article className="rounded-[1.75rem] border border-white/10 bg-black/25 p-6 backdrop-blur">
                    <div className="flex items-center justify-between gap-4">
                      <div>
                        <h2 className="font-display text-2xl text-white">
                          Pending limit orders
                        </h2>
                        <p className="text-sm text-stone-400">
                          These orders have reserved funds already, but they only become trades when the market crosses their limit.
                        </p>
                      </div>
                    </div>

                    <div className="mt-6 grid gap-4">
                      {pendingOrders.length ? (
                        pendingOrders.map((order) => (
                          <div key={order.orderId} className="rounded-[1.5rem] border border-white/10 bg-stone-950/70 p-5">
                            <div className="flex flex-wrap items-start justify-between gap-4">
                              <div>
                                <p className="text-xs uppercase tracking-[0.35em] text-stone-500">{order.targetTicker}</p>
                                <p className="mt-2 text-xl font-semibold text-white">{order.side} pending</p>
                                <p className="mt-2 text-sm text-stone-300">
                                  {order.currency === "USD"
                                    ? `Reserved ${formatMoney(order.amountReserved)}`
                                    : `Reserved ${formatAsset(order.amountReserved)} ${order.currency}`}
                                </p>
                                <p className="mt-2 text-xs text-stone-400">
                                  {getLimitTriggerLabel(order.side, order.targetTicker, order.targetPrice)}
                                </p>
                                <p className="mt-2 text-xs text-stone-500">Created {formatDate(order.createdAt)}</p>
                              </div>
                              <div className="flex flex-col items-end gap-3">
                                <span className="rounded-full bg-amber-300/12 px-3 py-1 text-xs uppercase tracking-[0.25em] text-amber-100 ring-1 ring-amber-300/25">
                                  waiting
                                </span>
                                <button
                                  className="rounded-2xl border border-rose-300/25 bg-rose-300/10 px-4 py-2 text-sm font-semibold text-rose-100 transition hover:bg-rose-300/20 disabled:cursor-not-allowed disabled:opacity-60"
                                  disabled={isBusy}
                                  onClick={() => handleCancelOrder(order.orderId)}
                                  type="button"
                                >
                                  {busyAction === `cancel-${order.orderId}` ? "Cancelling..." : "Cancel order"}
                                </button>
                              </div>
                            </div>
                          </div>
                        ))
                      ) : (
                        <div className="rounded-[1.5rem] border border-dashed border-white/15 bg-white/5 p-8 text-center text-sm text-stone-400">
                          No funds are currently reserved in pending limit orders.
                        </div>
                      )}
                    </div>
                  </article>

                  <article className="rounded-[1.75rem] border border-white/10 bg-black/25 p-6 backdrop-blur">
                    <div className="flex items-center justify-between">
                      <div>
                        <h2 className="font-display text-2xl text-white">
                          Execution history
                        </h2>
                        <p className="text-sm text-stone-400">
                          Every filled order emitted by the matching engine appears here.
                        </p>
                      </div>
                    </div>

                    <div className="mt-6 grid gap-4">
                      {(portfolio?.trades ?? []).length ? (
                        [...portfolio.trades].reverse().map((trade) => (
                          <div key={`${trade.orderId}-${trade.executedAt}`} className="rounded-[1.5rem] border border-white/10 bg-stone-950/70 p-5">
                            <div className="flex flex-wrap items-center justify-between gap-3">
                              <div>
                                <p className="text-xs uppercase tracking-[0.35em] text-stone-500">{trade.ticker}</p>
                                <p className="mt-2 text-xl font-semibold text-white">{trade.side} {formatAsset(trade.quantity)}</p>
                              </div>
                              <div className="text-right text-sm text-stone-300">
                                <p>{formatMoney(trade.totalCost)}</p>
                                <p>{formatDate(trade.executedAt)}</p>
                              </div>
                            </div>
                            <div className="mt-4 flex flex-wrap gap-3 text-xs text-stone-400">
                              <span className="rounded-full bg-white/5 px-3 py-1 ring-1 ring-white/10">
                                Price {formatMoney(trade.executionPrice)}
                              </span>
                              <span className="rounded-full bg-white/5 px-3 py-1 ring-1 ring-white/10">
                                Order {trade.orderId}
                              </span>
                            </div>
                          </div>
                        ))
                      ) : (
                        <div className="rounded-[1.5rem] border border-dashed border-white/15 bg-white/5 p-8 text-center text-sm text-stone-400">
                          {pendingOrders.length
                            ? "No execution yet. The reserved order is still waiting for the market to cross its limit price."
                            : "No execution has reached the analytics stream yet. Reserve an order and let the market feeder cross it."}
                        </div>
                      )}
                    </div>
                  </article>
                </div>
              </section>
            </>
          )}
        </main>
      </div>
    </div>
  );
}

export default App;
