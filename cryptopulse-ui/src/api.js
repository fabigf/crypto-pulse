import axios from "axios";

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export const apiClient = axios.create({
  baseURL: apiBaseUrl,
  timeout: 10000
});

export function getApiBaseUrl() {
  return apiBaseUrl;
}

export function getWebSocketUrl() {
  const explicitUrl = import.meta.env.VITE_WS_URL;
  if (explicitUrl) {
    return explicitUrl;
  }

  const normalizedUrl = apiBaseUrl.replace(/^http/, "ws");
  return `${normalizedUrl}/ws-market`;
}

function sleep(delayMs) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, delayMs);
  });
}

export async function waitForGatewayReadiness({ attempts = 6, delayMs = 1500 } = {}) {
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await apiClient.get("/api/v1/analytics/markets", {
        timeout: 3000
      });

      if (response.status >= 200 && response.status < 500) {
        return true;
      }
    } catch {
      if (attempt === attempts) {
        return false;
      }
    }

    if (attempt < attempts) {
      await sleep(delayMs);
    }
  }

  return false;
}
