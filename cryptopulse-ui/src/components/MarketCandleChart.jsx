import { useState } from "react";

function buildPriceDomain(candles) {
  const lows = candles.map((candle) => Number(candle.low));
  const highs = candles.map((candle) => Number(candle.high));
  const min = Math.min(...lows);
  const max = Math.max(...highs);
  const spread = Math.max(max - min, max * 0.01, 0.00000001);
  const padding = spread * 0.18;
  return {
    min: Math.max(0, min - padding),
    max: max + padding
  };
}

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max);
}

function formatBucketLabel(timestamp, timeframe) {
  const date = new Date(timestamp);

  if (timeframe === "1M") {
    return new Intl.DateTimeFormat("en-GB", {
      month: "short",
      year: "2-digit"
    }).format(date);
  }

  if (timeframe === "1w" || timeframe === "1d") {
    return new Intl.DateTimeFormat("en-GB", {
      day: "2-digit",
      month: "short"
    }).format(date);
  }

  return new Intl.DateTimeFormat("en-GB", {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}

function formatHoverPrice(price) {
  const numericPrice = Number(price ?? 0);
  return new Intl.NumberFormat("en-US", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 8
  }).format(numericPrice);
}

function mapPriceToY(price, minPrice, maxPrice, height, topPadding, bottomPadding) {
  const usableHeight = height - topPadding - bottomPadding;
  return topPadding + ((maxPrice - Number(price)) / (maxPrice - minPrice || 1)) * usableHeight;
}

function mapYToPrice(y, minPrice, maxPrice, height, topPadding, bottomPadding) {
  const usableHeight = height - topPadding - bottomPadding;
  const relativePosition = clamp((y - topPadding) / (usableHeight || 1), 0, 1);
  return maxPrice - relativePosition * (maxPrice - minPrice);
}

export function MarketCandleChart({ candles = [], timeframe = "1m", title = "Candles" }) {
  const [hoveredPoint, setHoveredPoint] = useState(null);

  if (!candles.length) {
    return (
      <div className="rounded-[1.5rem] border border-dashed border-white/15 bg-white/5 p-10 text-center text-sm text-stone-400">
        Waiting for market candles...
      </div>
    );
  }

  const chartWidth = 860;
  const chartHeight = 340;
  const topPadding = 24;
  const bottomPadding = 36;
  const leftPadding = 20;
  const rightPadding = 20;
  const plotTop = topPadding;
  const plotBottom = chartHeight - bottomPadding;
  const plotLeft = leftPadding;
  const plotRight = chartWidth - rightPadding;
  const { min, max } = buildPriceDomain(candles);
  const innerWidth = chartWidth - leftPadding - rightPadding;
  const step = innerWidth / Math.max(candles.length, 1);
  const candleBodyWidth = Math.max(step * 0.56, 6);

  let activeHover = null;
  if (hoveredPoint) {
    const hoverIndex = clamp(
      Math.round((hoveredPoint.x - leftPadding - step / 2) / step),
      0,
      candles.length - 1
    );
    const candle = candles[hoverIndex];
    const candleX = leftPadding + step * hoverIndex + step / 2;
    const clampedY = clamp(hoveredPoint.y, plotTop, plotBottom);
    const openY = mapPriceToY(candle.open, min, max, chartHeight, topPadding, bottomPadding);
    const closeY = mapPriceToY(candle.close, min, max, chartHeight, topPadding, bottomPadding);
    const highY = mapPriceToY(candle.high, min, max, chartHeight, topPadding, bottomPadding);
    const tooltipWidth = 156;
    const tooltipHeight = 62;
    const tooltipX = clamp(candleX + 14, plotLeft + 6, chartWidth - tooltipWidth - 8);
    const tooltipY = clamp(
      Math.min(openY, closeY, highY) - tooltipHeight - 12,
      plotTop + 6,
      plotBottom - tooltipHeight - 6
    );

    activeHover = {
      candle,
      candleX,
      hoveredPrice: mapYToPrice(clampedY, min, max, chartHeight, topPadding, bottomPadding),
      hoverY: clampedY,
      hoverIndex,
      tooltipHeight,
      tooltipWidth,
      tooltipX,
      tooltipY
    };
  }

  function handlePointerMove(event) {
    const bounds = event.currentTarget.getBoundingClientRect();
    const scaleX = chartWidth / bounds.width;
    const scaleY = chartHeight / bounds.height;
    const nextX = clamp((event.clientX - bounds.left) * scaleX, plotLeft, plotRight);
    const nextY = clamp((event.clientY - bounds.top) * scaleY, plotTop, plotBottom);

    setHoveredPoint({
      x: nextX,
      y: nextY
    });
  }

  return (
    <div className="overflow-hidden rounded-[1.75rem] border border-white/10 bg-stone-950/70 p-4">
      <div className="mb-4 flex items-center justify-between gap-3 px-2">
        <div>
          <p className="text-xs uppercase tracking-[0.35em] text-stone-500">{title}</p>
          <p className="mt-1 text-sm text-stone-300">Timeframe {timeframe}</p>
        </div>
      </div>
      <svg
        className="h-auto w-full"
        viewBox={`0 0 ${chartWidth} ${chartHeight}`}
        role="img"
        onMouseLeave={() => setHoveredPoint(null)}
        onMouseMove={handlePointerMove}
      >
        <defs>
          <linearGradient id="chartBackdrop" x1="0%" x2="100%" y1="0%" y2="100%">
            <stop offset="0%" stopColor="rgba(125,211,252,0.18)" />
            <stop offset="100%" stopColor="rgba(251,191,36,0.04)" />
          </linearGradient>
        </defs>

        <rect fill="url(#chartBackdrop)" height={chartHeight} rx="28" width={chartWidth} x="0" y="0" />

        {[0, 1, 2, 3].map((lineIndex) => {
          const y = topPadding + ((chartHeight - topPadding - bottomPadding) / 3) * lineIndex;
          return (
            <line
              key={lineIndex}
              stroke="rgba(255,255,255,0.08)"
              strokeDasharray="4 8"
              x1={leftPadding}
              x2={chartWidth - rightPadding}
              y1={y}
              y2={y}
            />
          );
        })}

        {activeHover ? (
          <rect
            fill="rgba(251,191,36,0.08)"
            height={plotBottom - plotTop}
            rx="8"
            width={Math.max(step - 8, candleBodyWidth + 8)}
            x={activeHover.candleX - Math.max(step - 8, candleBodyWidth + 8) / 2}
            y={plotTop}
          />
        ) : null}

        {candles.map((candle, index) => {
          const x = leftPadding + step * index + step / 2;
          const highY = mapPriceToY(candle.high, min, max, chartHeight, topPadding, bottomPadding);
          const lowY = mapPriceToY(candle.low, min, max, chartHeight, topPadding, bottomPadding);
          const openY = mapPriceToY(candle.open, min, max, chartHeight, topPadding, bottomPadding);
          const closeY = mapPriceToY(candle.close, min, max, chartHeight, topPadding, bottomPadding);
          const bodyTop = Math.min(openY, closeY);
          const bodyHeight = Math.max(Math.abs(openY - closeY), 2);
          const rising = Number(candle.close) >= Number(candle.open);
          const color = rising ? "#86efac" : "#fca5a5";
          const isHovered = activeHover?.hoverIndex === index;

          return (
            <g key={`${candle.bucketStart}-${index}`}>
              <line
                stroke={color}
                strokeOpacity={isHovered ? "1" : "0.9"}
                strokeWidth={isHovered ? "3" : "2"}
                x1={x}
                x2={x}
                y1={highY}
                y2={lowY}
              />
              <rect
                fill={color}
                fillOpacity={isHovered ? "1" : "0.92"}
                height={bodyHeight}
                rx="3"
                stroke={isHovered ? "rgba(255,255,255,0.7)" : "none"}
                strokeWidth={isHovered ? "1.2" : "0"}
                width={candleBodyWidth}
                x={x - candleBodyWidth / 2}
                y={bodyTop}
              />
              {index % Math.max(Math.floor(candles.length / 6), 1) === 0 ? (
                <text
                  fill="rgba(231,229,228,0.78)"
                  fontSize="12"
                  textAnchor="middle"
                  x={x}
                  y={chartHeight - 10}
                >
                  {formatBucketLabel(candle.bucketStart, timeframe)}
                </text>
              ) : null}
            </g>
          );
        })}

        {activeHover ? (
          <>
            <line
              stroke="rgba(255,255,255,0.28)"
              strokeDasharray="6 6"
              x1={plotLeft}
              x2={plotRight}
              y1={activeHover.hoverY}
              y2={activeHover.hoverY}
            />
            <line
              stroke="rgba(255,255,255,0.28)"
              strokeDasharray="6 6"
              x1={activeHover.candleX}
              x2={activeHover.candleX}
              y1={plotTop}
              y2={plotBottom}
            />

            <g>
              <rect
                fill="rgba(12,10,9,0.96)"
                height="24"
                rx="8"
                stroke="rgba(251,191,36,0.45)"
                width="92"
                x={chartWidth - rightPadding - 92}
                y={activeHover.hoverY - 12}
              />
              <text
                fill="rgba(255,251,235,0.96)"
                fontSize="12"
                fontWeight="600"
                textAnchor="middle"
                x={chartWidth - rightPadding - 46}
                y={activeHover.hoverY + 4}
              >
                {formatHoverPrice(activeHover.hoveredPrice)}
              </text>
            </g>

            <g>
              <rect
                fill="rgba(12,10,9,0.96)"
                height="24"
                rx="8"
                stroke="rgba(125,211,252,0.4)"
                width="94"
                x={clamp(activeHover.candleX - 47, plotLeft, plotRight - 94)}
                y={chartHeight - bottomPadding + 6}
              />
              <text
                fill="rgba(231,229,228,0.96)"
                fontSize="12"
                fontWeight="600"
                textAnchor="middle"
                x={clamp(activeHover.candleX, plotLeft + 47, plotRight - 47)}
                y={chartHeight - 14}
              >
                {formatBucketLabel(activeHover.candle.bucketStart, timeframe)}
              </text>
            </g>

            <g>
              <rect
                fill="rgba(12,10,9,0.96)"
                height={activeHover.tooltipHeight}
                rx="12"
                stroke="rgba(255,255,255,0.14)"
                width={activeHover.tooltipWidth}
                x={activeHover.tooltipX}
                y={activeHover.tooltipY}
              />
              <text
                fill="rgba(251,191,36,0.96)"
                fontSize="11"
                fontWeight="700"
                x={activeHover.tooltipX + 12}
                y={activeHover.tooltipY + 18}
              >
                Candle {formatBucketLabel(activeHover.candle.bucketStart, timeframe)}
              </text>
              <text
                fill="rgba(231,229,228,0.94)"
                fontSize="12"
                x={activeHover.tooltipX + 12}
                y={activeHover.tooltipY + 38}
              >
                Open {formatHoverPrice(activeHover.candle.open)}
              </text>
              <text
                fill="rgba(231,229,228,0.94)"
                fontSize="12"
                x={activeHover.tooltipX + 12}
                y={activeHover.tooltipY + 54}
              >
                Close {formatHoverPrice(activeHover.candle.close)}
              </text>
            </g>
          </>
        ) : null}

        <rect
          fill="transparent"
          height={plotBottom - plotTop}
          width={plotRight - plotLeft}
          x={plotLeft}
          y={plotTop}
        />

        <text fill="rgba(231,229,228,0.92)" fontSize="12" textAnchor="end" x={chartWidth - 10} y={20}>
          {max.toFixed(2)}
        </text>
        <text fill="rgba(231,229,228,0.92)" fontSize="12" textAnchor="end" x={chartWidth - 10} y={chartHeight - 14}>
          {min.toFixed(2)}
        </text>
      </svg>
    </div>
  );
}
