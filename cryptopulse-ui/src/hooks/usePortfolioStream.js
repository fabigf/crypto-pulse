import { useEffect, useEffectEvent, useRef } from "react";
import { Client } from "@stomp/stompjs";
import { getWebSocketUrl } from "../api";

export function usePortfolioStream({ userId, enabled = true, onPortfolio, onStatus }) {
  const clientRef = useRef(null);
  const emitPortfolio = useEffectEvent(onPortfolio);
  const emitStatus = useEffectEvent(onStatus);

  useEffect(() => {
    if (!userId) {
      emitStatus("idle");
      return undefined;
    }

    if (!enabled) {
      emitStatus("connecting");
      return undefined;
    }

    let isActive = true;
    let hasConnected = false;
    let offlineTimer = null;
    let client = null;

    const updateStatus = (nextStatus) => {
      if (isActive) {
        emitStatus(nextStatus);
      }
    };

    emitStatus("connecting");
    offlineTimer = window.setTimeout(() => {
      if (!hasConnected) {
        updateStatus("offline");
      }
    }, 9000);

    client = new Client({
      brokerURL: getWebSocketUrl(),
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        hasConnected = true;
        window.clearTimeout(offlineTimer);
        updateStatus("live");
        client.subscribe(`/topic/portfolio/${userId}`, (message) => {
          emitPortfolio(JSON.parse(message.body));
        });
      },
      onDisconnect: () => updateStatus("disconnected"),
      onStompError: () => updateStatus(hasConnected ? "reconnecting" : "connecting"),
      onWebSocketError: () => updateStatus(hasConnected ? "reconnecting" : "connecting"),
      onWebSocketClose: () => {
        if (!isActive) {
          return;
        }

        hasConnected = false;
        updateStatus("reconnecting");
      }
    });

    client.activate();
    clientRef.current = client;

    return () => {
      isActive = false;
      window.clearTimeout(offlineTimer);
      emitStatus("disconnected");
      client?.deactivate();
      clientRef.current = null;
    };
  }, [enabled, userId]);
}
