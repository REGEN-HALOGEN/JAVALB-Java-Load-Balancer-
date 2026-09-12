import { LB_WS_URL } from "./api";
import { useLiveStore } from "./store";

/**
 * Opens the WebSocket live stream to the Java load balancer and fans
 * incoming messages into the zustand store. Auto-reconnects with
 * exponential backoff. Returns a cleanup function.
 */
export function connectLiveStream(): () => void {
  let ws: WebSocket | null = null;
  let closed = false;
  let retry = 0;

  function connect() {
    if (closed) return;
    useLiveStore.getState().setConnected(false);
    ws = new WebSocket(LB_WS_URL);

    ws.onopen = () => {
      retry = 0;
      useLiveStore.getState().setConnected(true);
    };

    ws.onmessage = (e: MessageEvent) => {
      try {
        const msg = JSON.parse(e.data as string) as {
          type?: string;
        };
        if (msg.type === "snapshot") {
          useLiveStore.getState().pushSnapshot(msg as never);
        } else if (msg.type === "request") {
          useLiveStore.getState().pushRequest(msg as never);
        }
      } catch {
        // ignore malformed frames
      }
    };

    ws.onclose = () => {
      useLiveStore.getState().setConnected(false);
      if (!closed) {
        const delay = Math.min(1000 * 2 ** retry, 10000);
        retry++;
        setTimeout(connect, delay);
      }
    };

    ws.onerror = () => {
      try {
        ws?.close();
      } catch {
        // ignore
      }
    };
  }

  connect();

  return () => {
    closed = true;
    try {
      ws?.close();
    } catch {
      // ignore
    }
  };
}