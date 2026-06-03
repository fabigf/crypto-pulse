import { MarketCandleChart } from "./MarketCandleChart";

const timeframeOptions = [
  { value: "4h", label: "4H" },
  { value: "1d", label: "1D" },
  { value: "1w", label: "1W" },
  { value: "1M", label: "1M" }
];

function getTimeframeTone(isActive) {
  return isActive
    ? "bg-amber-300 text-stone-950"
    : "border border-white/10 bg-stone-950/80 text-white hover:border-amber-300/30";
}

function getPairTone(isActive) {
  return isActive
    ? "border-amber-300/35 bg-amber-300/10 ring-1 ring-amber-200/30"
    : "border-white/10 bg-white/5 hover:border-sky-300/25 hover:bg-sky-300/5";
}

export function MarketView({
  chartStatus,
  formatDate,
  formatMoney,
  formatPairLabel,
  formatPercent,
  getConnectionTone,
  getDeltaTone,
  marketChart,
  marketPairs,
  marketSnapshot,
  marketStatus,
  selectedTicker,
  selectedTimeframe,
  setSelectedTicker,
  setSelectedTimeframe
}) {
  const chartCandles = marketChart?.candles ?? [];
  const activeCandle = chartCandles.at(-1);
  const effectivePrice = marketSnapshot?.latestPrice ?? marketChart?.latestPrice;
  const effectiveAbsoluteChange = marketSnapshot?.absoluteChange ?? marketChart?.absoluteChange;
  const effectivePercentChange = marketSnapshot?.percentageChange ?? marketChart?.percentageChange;

  return (
    <section className="grid gap-6">
      <article className="rounded-[2rem] border border-sky-200/10 bg-black/25 p-6 backdrop-blur">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="max-w-3xl">
            <p className="text-xs font-semibold uppercase tracking-[0.4em] text-sky-200/70">
              Market workspace
            </p>
            <h2 className="font-display mt-3 text-3xl text-white sm:text-4xl">
              {formatPairLabel(selectedTicker)} multi-timeframe board
            </h2>
            <p className="mt-3 text-sm leading-7 text-stone-400">
              This screen mixes the live STOMP snapshot with historical Binance candles, so we can inspect the pair on
              `4h`, `1d`, `1w` and `1M` without leaving the platform.
            </p>
          </div>

          <div className="flex flex-wrap gap-3 text-xs">
            <span className={`rounded-full px-4 py-2 ring-1 ${getConnectionTone(marketStatus)}`}>
              Stream: {marketStatus}
            </span>
            <span className={`rounded-full px-4 py-2 ring-1 ${getConnectionTone(chartStatus === "ready" ? "live" : chartStatus === "loading" ? "connecting" : chartStatus)}`}>
              Chart: {chartStatus}
            </span>
            <span className="rounded-full bg-white/10 px-4 py-2 ring-1 ring-white/15">
              Source: {marketChart?.source ?? "binance-uiKlines"}
            </span>
          </div>
        </div>

        <div className="mt-6 grid gap-4 xl:grid-cols-[0.85fr_1.15fr]">
          <div className="grid gap-4">
            <label className="rounded-[1.5rem] border border-white/10 bg-stone-950/80 p-4">
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

            <div className="rounded-[1.5rem] border border-white/10 bg-stone-950/80 p-4">
              <p className="text-xs uppercase tracking-[0.3em] text-stone-500">Timeframe</p>
              <div className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
                {timeframeOptions.map((option) => (
                  <button
                    key={option.value}
                    className={`rounded-2xl px-4 py-3 text-sm font-semibold transition ${getTimeframeTone(option.value === selectedTimeframe)}`}
                    type="button"
                    onClick={() => setSelectedTimeframe(option.value)}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="rounded-[1.5rem] border border-white/10 bg-white/5 p-4">
                <p className="text-xs uppercase tracking-[0.3em] text-stone-500">Last price</p>
                <p className="mt-2 text-2xl font-semibold text-white">{formatMoney(effectivePrice)}</p>
              </div>
              <div className="rounded-[1.5rem] border border-white/10 bg-white/5 p-4">
                <p className="text-xs uppercase tracking-[0.3em] text-stone-500">Move</p>
                <p className={`mt-2 text-2xl font-semibold ${getDeltaTone(effectivePercentChange)}`}>
                  {formatPercent(effectivePercentChange)}
                </p>
              </div>
              <div className="rounded-[1.5rem] border border-white/10 bg-white/5 p-4">
                <p className="text-xs uppercase tracking-[0.3em] text-stone-500">Absolute</p>
                <p className={`mt-2 text-2xl font-semibold ${getDeltaTone(effectiveAbsoluteChange)}`}>
                  {formatMoney(effectiveAbsoluteChange)}
                </p>
              </div>
              <div className="rounded-[1.5rem] border border-white/10 bg-white/5 p-4">
                <p className="text-xs uppercase tracking-[0.3em] text-stone-500">Updated</p>
                <p className="mt-2 text-sm font-semibold text-stone-100">
                  {formatDate(marketSnapshot?.updatedAt ?? marketChart?.updatedAt)}
                </p>
              </div>
            </div>
          </div>

          <div className="grid gap-4">
            <MarketCandleChart
              candles={chartCandles}
              timeframe={selectedTimeframe}
              title={`${formatPairLabel(selectedTicker)} market candles`}
            />

            <div className="grid gap-4 sm:grid-cols-4">
              <div className="rounded-[1.5rem] border border-white/10 bg-white/5 p-4">
                <p className="text-xs uppercase tracking-[0.3em] text-stone-500">Open</p>
                <p className="mt-2 text-xl font-semibold text-white">
                  {formatMoney(activeCandle?.open)}
                </p>
              </div>
              <div className="rounded-[1.5rem] border border-white/10 bg-white/5 p-4">
                <p className="text-xs uppercase tracking-[0.3em] text-stone-500">High</p>
                <p className="mt-2 text-xl font-semibold text-emerald-200">
                  {formatMoney(activeCandle?.high)}
                </p>
              </div>
              <div className="rounded-[1.5rem] border border-white/10 bg-white/5 p-4">
                <p className="text-xs uppercase tracking-[0.3em] text-stone-500">Low</p>
                <p className="mt-2 text-xl font-semibold text-rose-200">
                  {formatMoney(activeCandle?.low)}
                </p>
              </div>
              <div className="rounded-[1.5rem] border border-white/10 bg-white/5 p-4">
                <p className="text-xs uppercase tracking-[0.3em] text-stone-500">Close</p>
                <p className="mt-2 text-xl font-semibold text-sky-200">
                  {formatMoney(activeCandle?.close)}
                </p>
              </div>
            </div>
          </div>
        </div>
      </article>

      <article className="rounded-[1.75rem] border border-white/10 bg-black/25 p-6 backdrop-blur">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <h3 className="font-display text-2xl text-white">Tracked pairs</h3>
            <p className="text-sm text-stone-400">
              Quick switch between the supported spot markets that the platform is polling right now.
            </p>
          </div>
        </div>

        <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          {marketPairs.map((market) => (
            <button
              key={market.ticker}
              className={`rounded-[1.5rem] border p-4 text-left transition ${getPairTone(market.ticker === selectedTicker)}`}
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
      </article>
    </section>
  );
}
