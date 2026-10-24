import { useCallback, useEffect, useRef, useState } from "react";
import {
  API_BASE,
  POLL_MS,
  WS_URL,
  fetchJson,
  sigId,
  sigName,
  sigState,
  type Ambulance,
  type Hospital,
  type Signal,
} from "@/lib/traffic";

export type LogEntry = {
  id: number;
  time: Date;
  type: "info" | "success" | "warning" | "danger";
  message: string;
};
export type Conn = "connecting" | "connected" | "disconnected";

let logSeq = 0;

export function useTrafficData() {
  const [ambulances, setAmbulances] = useState<Ambulance[]>([]);
  const [signals, setSignals] = useState<Signal[]>([]);
  const [hospitals, setHospitals] = useState<Hospital[]>([]);
  const [loading, setLoading] = useState(true);
  const [received, setReceived] = useState({ ambulances: false, signals: false, hospitals: false });
  const [error, setError] = useState<string | null>(null);
  const [backend, setBackend] = useState<Conn>("connecting");
  const [ws, setWs] = useState<Conn>("connecting");
  const [lastSync, setLastSync] = useState<Date | null>(null);
  const [log, setLog] = useState<LogEntry[]>([]);
  const backendRef = useRef<Conn>("connecting");
  const wsRef = useRef<Conn>("connecting");

  const addLog = useCallback((type: LogEntry["type"], message: string) => {
    setLog((l) => [{ id: ++logSeq, time: new Date(), type, message }, ...l].slice(0, 120));
  }, []);

  const refresh = useCallback(async () => {
    const [a, s, h] = await Promise.allSettled([
      fetchJson<Ambulance[]>("/api/ambulances"),
      fetchJson<Signal[]>("/api/signals"),
      fetchJson<Hospital[]>("/api/hospitals"),
    ]);
    if (a.status === "fulfilled") setAmbulances(a.value);
    if (s.status === "fulfilled") setSignals(s.value);
    if (h.status === "fulfilled") setHospitals(h.value);
    setReceived((prev) => ({
      ambulances: prev.ambulances || a.status === "fulfilled",
      signals: prev.signals || s.status === "fulfilled",
      hospitals: prev.hospitals || h.status === "fulfilled",
    }));
    const ok = a.status === "fulfilled" && s.status === "fulfilled" && h.status === "fulfilled";
    const prev = backendRef.current;
    if (ok) {
      setError(null);
      setLastSync(new Date());
      if (prev !== "connected")
        addLog(
          "success",
          prev === "disconnected" ? "Backend connection restored" : "Connected to backend",
        );
      backendRef.current = "connected";
      setBackend("connected");
    } else {
      const reason = [a, s, h]
        .flatMap((result) =>
          result.status === "rejected" ? [String(result.reason?.message ?? result.reason)] : [],
        )
        .join("; ");
      setError(reason);
      if (prev !== "disconnected") addLog("danger", "Backend connection lost");
      backendRef.current = "disconnected";
      setBackend("disconnected");
    }
    setLoading(false);
  }, [addLog]);

  const setEmergency = useCallback(
    async (ambulanceId: string, emergencyStatus: boolean) => {
      // The Spring Boot emergency endpoint already calculates the route,
      // synchronizes signal priorities, and starts GPS simulation.
      // Do not call separate route/simulation URLs here: those paths differ
      // from the controller mappings and can make a successful dispatch look failed.
      const response = await fetch(
        `${API_BASE}/api/ambulances/${encodeURIComponent(ambulanceId)}/emergency?emergencyStatus=${emergencyStatus}`,
        { method: "PUT", headers: { Accept: "application/json" } },
      );

      if (!response.ok) {
        const details = await response.text().catch(() => "");
        throw new Error(details || `Failed to update emergency status (${response.status})`);
      }

      // Pull authoritative ambulance/signal/hospital state after the backend action.
      await refresh();
      addLog(
        "success",
        emergencyStatus
          ? `Ambulance ${ambulanceId}: emergency activated; GPS tracking started and route signals prioritized`
          : `Ambulance ${ambulanceId}: emergency deactivated`,
      );
    },
    [addLog, refresh],
  );

  const controlSimulation = useCallback(
    async (action: "start" | "stop" | "reset") => {
      const response = await fetch(`${API_BASE}/api/ambulances/simulation/${action}`, {
        method: "POST",
        headers: { Accept: "application/json" },
      });

      if (!response.ok) {
        const details = await response.text().catch(() => "");
        throw new Error(details || `Failed to ${action} simulation (${response.status})`);
      }

      await refresh();
      addLog(
        action === "start" ? "success" : action === "stop" ? "warning" : "info",
        `Simulation ${action} command completed`,
      );
    },
    [addLog, refresh],
  );

  // Demo mode: automatically dispatch one ambulance when backend data first arrives.
  // In a production deployment, replace this demo dispatch with a real dispatch/GPS event.
  const autoDispatchStarted = useRef(false);
  useEffect(() => {
    if (autoDispatchStarted.current || backend !== "connected" || ambulances.length === 0) return;

    autoDispatchStarted.current = true;
    const active = ambulances.find((ambulance) => {
      const status = String(ambulance.emergencyStatus ?? "")
        .trim()
        .toLowerCase();
      return (
        ambulance.emergencyStatus === true ||
        status === "true" ||
        ["active", "emergency", "en_route", "enroute", "critical", "on_duty"].includes(status)
      );
    });
    const target = active ?? ambulances[0];
    if (!target) return;

    void setEmergency(String(target.ambulanceId), true).catch((error) => {
      addLog(
        "danger",
        `Automatic dispatch failed: ${error instanceof Error ? error.message : String(error)}`,
      );
    });
  }, [ambulances, backend, setEmergency, addLog]);

  // REST: initial load + polling fallback (fast when WS down, slow when up)
  useEffect(() => {
    let alive = true;
    let timer: ReturnType<typeof setTimeout>;
    const tick = async () => {
      await refresh();
      if (alive) timer = setTimeout(tick, wsRef.current === "connected" ? POLL_MS * 4 : POLL_MS);
    };
    tick();
    return () => {
      alive = false;
      clearTimeout(timer);
    };
  }, [refresh]);

  // WebSocket / STOMP
  useEffect(() => {
    let client: import("@stomp/stompjs").Client | null = null;
    let cancelled = false;
    (async () => {
      const { Client } = await import("@stomp/stompjs");
      if (cancelled) return;
      client = new Client({
        brokerURL: WS_URL,
        reconnectDelay: 5000,
        heartbeatIncoming: 10000,
        heartbeatOutgoing: 10000,
        onConnect: () => {
          const connectedClient = client;
          if (!connectedClient || cancelled) return;
          wsRef.current = "connected";
          setWs("connected");
          addLog("success", "WebSocket live channel connected");
          const parse = (body: string) => {
            try {
              return JSON.parse(body);
            } catch {
              return body;
            }
          };
          connectedClient.subscribe("/topic/ambulance/update", (m) => {
            const a = parse(m.body) as Ambulance;
            if (!a || typeof a !== "object" || a.ambulanceId == null) return;
            setAmbulances((list) => {
              const i = list.findIndex((x) => String(x.ambulanceId) === String(a.ambulanceId));
              if (i >= 0) {
                const prev = list[i];
                if (!prev) return list;
                if (prev.emergencyStatus !== a.emergencyStatus && a.emergencyStatus)
                  addLog("warning", `Ambulance ${a.ambulanceId} status → ${a.emergencyStatus}`);
                const next = [...list];
                next[i] = { ...prev, ...a };
                return next;
              }
              return [...list, a];
            });
            addLog("info", `Ambulance ${a.ambulanceId} location updated`);
          });
          connectedClient.subscribe("/topic/ambulance/arrived", (m) => {
            const a = parse(m.body);
            const id = typeof a === "object" && a ? (a as Ambulance).ambulanceId : a;
            addLog("success", `Ambulance ${id ?? ""} arrival detected`);
            if (typeof a === "object" && a && (a as Ambulance).ambulanceId != null)
              setAmbulances((l) =>
                l.map((x) =>
                  String(x.ambulanceId) === String((a as Ambulance).ambulanceId)
                    ? { ...x, ...(a as Ambulance) }
                    : x,
                ),
              );
          });
          connectedClient.subscribe("/topic/signal/update", (m) => {
            const payload = parse(m.body) as Signal | { signal?: Signal; ambulanceId?: string; eventType?: string };
            // Backend publishes SignalUpdateEvent { signal, ambulanceId, eventType }.
            // Unwrap it before updating the dashboard; otherwise the signalId is undefined
            // and the UI silently ignores every WebSocket signal update.
            const s = (payload && typeof payload === "object" && "signal" in payload
              ? payload.signal
              : payload) as Signal;
            if (!s || typeof s !== "object" || s.signalId == null) return;
            setSignals((list) => {
              const i = list.findIndex((x) => sigId(x) === sigId(s));
              if (i >= 0) {
                const next = [...list];
                next[i] = { ...list[i], ...s };
                return next;
              }
              return [...list, s];
            });
            addLog("info", `${sigName(s)} → ${sigState(s)}`);
          });
          connectedClient.subscribe("/topic/system/event", (m) => {
            const e = parse(m.body);
            const msg = typeof e === "string" ? e : (e?.message ?? e?.event ?? JSON.stringify(e));
            addLog("info", String(msg));
          });
        },
        onWebSocketClose: () => {
          if (wsRef.current === "connected")
            addLog("warning", "WebSocket disconnected — using REST polling");
          wsRef.current = "disconnected";
          setWs("disconnected");
        },
        onStompError: () => {
          wsRef.current = "disconnected";
          setWs("disconnected");
        },
        debug: () => {},
      });
      client.activate();
    })();
    return () => {
      cancelled = true;
      client?.deactivate();
    };
  }, [addLog]);

  return {
    ambulances,
    signals,
    hospitals,
    received,
    loading,
    error,
    backend,
    ws,
    lastSync,
    log,
    refresh,
    setEmergency,
    controlSimulation,
  };
}
