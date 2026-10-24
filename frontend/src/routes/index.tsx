import { createFileRoute, ClientOnly } from "@tanstack/react-router";
import { lazy, Suspense, useEffect, useMemo, useState } from "react";
import { useTrafficData, type Conn, type LogEntry } from "@/hooks/use-traffic-data";
import {
  API_BASE,
  connectionHint,
  emergencyTone,
  fmt,
  hospName,
  summarizeTraffic,
  sigAmbulance,
  sigId,
  sigName,
  sigPriority,
  sigState,
  stateTone,
  type Ambulance,
  type StatusTone,
} from "@/lib/traffic";

const LiveMap = lazy(() => import("@/components/dashboard/LiveMap"));

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "Smart Traffic Signal Control — Ambulance GPS Dashboard" },
      {
        name: "description",
        content:
          "Control-room dashboard for ambulance tracking and intelligent traffic signal priority.",
      },
      { property: "og:title", content: "Smart Traffic Signal Control System" },
      {
        property: "og:description",
        content: "Live ambulance tracking and traffic signal priority monitoring.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: Dashboard,
});

function Dot({ tone, pulse }: { tone: StatusTone; pulse?: boolean }) {
  return <span className={`status-dot tone-${tone} ${pulse ? "animate-pulse" : ""}`} />;
}
function Badge({ tone, children }: { tone: StatusTone; children: React.ReactNode }) {
  return <span className={`status-badge tone-${tone}`}>{children}</span>;
}
const connTone = (c: Conn): StatusTone =>
  c === "connected" ? "success" : c === "connecting" ? "warning" : "danger";

function Clock() {
  const [now, setNow] = useState<Date | null>(null);
  useEffect(() => {
    setNow(new Date());
    const t = setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(t);
  }, []);
  return (
    <div className="text-right font-mono">
      <div className="text-lg font-semibold tabular-nums">
        {now ? now.toLocaleTimeString() : "--:--:--"}
      </div>
      <div className="text-xs opacity-70">
        {now
          ? now.toLocaleDateString(undefined, {
              weekday: "short",
              day: "numeric",
              month: "short",
              year: "numeric",
            })
          : ""}
      </div>
    </div>
  );
}

function Dashboard() {
  const d = useTrafficData();
  const [selected, setSelected] = useState<string | null>(null);

  // Keep the map focused on the active ambulance for the live demo.
  // If no emergency is active, use AMB001 (or the first available ambulance).
  useEffect(() => {
    if (d.ambulances.length === 0) return;
    const active = d.ambulances.find((ambulance) => {
      const status = String(ambulance.emergencyStatus ?? "").trim().toLowerCase();
      return ambulance.emergencyStatus === true || status === "true" ||
        ["active", "emergency", "en_route", "enroute", "critical", "on_duty"].includes(status);
    });
    const preferred = active ?? d.ambulances.find((ambulance) => String(ambulance.ambulanceId) === "AMB001") ?? d.ambulances[0];
    if (preferred && (!selected || !d.ambulances.some((ambulance) => String(ambulance.ambulanceId) === selected))) {
      setSelected(String(preferred.ambulanceId));
    }
  }, [d.ambulances, selected]);

  /**
   * Demo-only automatic dispatch. Enable explicitly with
   * VITE_DEMO_AUTO_DISPATCH=true in the frontend .env file.
   * Real deployments should receive emergencies from a real dispatch system.
   */
  useEffect(() => {
    if (import.meta.env["VITE_DEMO_AUTO_DISPATCH"] !== "true") return;
    if (d.backend !== "connected" || d.loading || d.ambulances.length === 0) return;

    const dispatchKey = "smart-traffic-demo-dispatched";
    if (sessionStorage.getItem(dispatchKey) === "true") return;

    const isEmergency = (status: unknown) => {
      const value = String(status ?? "")
        .trim()
        .toLowerCase();
      return ["true", "active", "emergency", "yes"].includes(value);
    };

    if (d.ambulances.some((ambulance) => isEmergency(ambulance.emergencyStatus))) {
      sessionStorage.setItem(dispatchKey, "true");
      return;
    }

    const ambulance = d.ambulances.find((item) => {
      const latitude = Number(item.currentLatitude);
      const longitude = Number(item.currentLongitude);
      return (
        Boolean(item.destinationHospital?.trim()) &&
        Number.isFinite(latitude) &&
        latitude >= -90 &&
        latitude <= 90 &&
        Number.isFinite(longitude) &&
        longitude >= -180 &&
        longitude <= 180 &&
        !(latitude === 0 && longitude === 0)
      );
    });

    if (!ambulance) return;

    // Set before the request to prevent repeated dispatches during polling.
    sessionStorage.setItem(dispatchKey, "true");
    const id = encodeURIComponent(String(ambulance.ambulanceId));

    fetch(`${API_BASE}/api/ambulances/${id}/emergency?emergencyStatus=true`, {
      method: "PUT",
      headers: { Accept: "application/json" },
    })
      .then(async (response) => {
        if (!response.ok) {
          const body = await response.text();
          throw new Error(body || `HTTP ${response.status}`);
        }
      })
      .catch((error: unknown) => {
        console.error("Automatic demo dispatch failed:", error);
        sessionStorage.removeItem(dispatchKey);
      });
  }, [d.backend, d.loading, d.ambulances]);

  const stats = useMemo(() => summarizeTraffic(d.ambulances, d.signals), [d.ambulances, d.signals]);
  const [hint, setHint] = useState("");
  useEffect(() => {
    setHint(connectionHint(API_BASE, window.location.protocol));
  }, []);

  const noData = !d.received.ambulances && !d.ambulances.length;

  return (
    <div className="min-h-screen bg-background">
      <header className="control-header">
        <div className="mx-auto flex max-w-[1600px] flex-wrap items-center justify-between gap-4 px-4 py-4 md:px-8">
          <div className="flex items-center gap-3">
            <div className="grid h-11 w-11 place-items-center rounded-xl bg-header-accent text-xl font-bold">
              ✚
            </div>
            <div>
              <h1 className="font-display text-lg font-semibold leading-tight md:text-2xl">
                Smart Traffic Signal Control System
              </h1>
              <p className="text-xs opacity-70 md:text-sm">
                Ambulance priority · GPS-based signal pre-emption
              </p>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-3 md:gap-6">
            <div className="header-chip">
              <Dot tone={connTone(d.backend)} pulse={d.backend === "connected"} /> Backend:{" "}
              {d.backend}
            </div>
            <div className="header-chip">
              <Dot tone={connTone(d.ws)} pulse={d.ws === "connected"} /> WebSocket:{" "}
              {d.ws === "connected"
                ? "live"
                : d.ws === "connecting"
                  ? "connecting"
                  : "offline (polling)"}
            </div>
            <Clock />
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-[1600px] space-y-6 px-4 py-6 md:px-8">
        {d.error && d.backend === "disconnected" && (
          <div className="panel tone-panel-danger flex flex-wrap items-center justify-between gap-2 p-4 text-sm">
            <span className="min-w-0 break-words">
              <b>Backend requests failed</b> at <code className="font-mono">{API_BASE}</code> —{" "}
              {d.error}. Retrying automatically. {hint}
            </span>
            {d.lastSync && (
              <span className="text-muted-foreground">
                Last successful sync: {d.lastSync.toLocaleTimeString()}
              </span>
            )}
          </div>
        )}

        <section className="panel p-4">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div>
              <h2 className="font-display text-base font-semibold">Ambulance Tracking</h2>
              <p className="text-sm text-muted-foreground">
                Live monitoring of ambulance GPS locations and emergency signal priorities.
              </p>
            </div>
            <div className="flex items-center gap-2 rounded-lg border border-border px-3 py-2 text-sm text-muted-foreground">
              <span className="h-2 w-2 animate-pulse rounded-full bg-green-500" />
              Live Monitoring · Emergency-triggered GPS tracking
            </div>
          </div>
        </section>

        <section className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <StatCard
            label="Total Ambulances"
            value={stats.total}
            tone="info"
            loading={d.loading}
            empty={noData}
          />
          <StatCard
            label="Active Emergencies"
            value={stats.active}
            tone="danger"
            loading={d.loading}
            empty={noData}
          />
          <StatCard
            label="Active Signal Priorities"
            value={stats.priorities}
            tone="success"
            loading={d.loading}
            empty={!d.received.signals && !d.signals.length}
          />
          <StatCard
            label="Completed Journeys"
            value={stats.completed}
            tone="muted"
            loading={d.loading}
            empty={noData}
          />
        </section>

        <section className="grid gap-6 xl:grid-cols-[1fr_380px]">
          <div className="panel overflow-hidden">
            <PanelHead
              title="Live Map"
              sub={`${d.ambulances.length} ambulances · ${d.signals.length} signals · ${d.hospitals.length} hospitals · OpenStreetMap`}
            >
              <select
                className="field"
                value={selected ?? ""}
                onChange={(e) => setSelected(e.target.value || null)}
              >
                <option value="">Focus ambulance…</option>
                {d.ambulances.map((a) => (
                  <option key={String(a.ambulanceId)} value={String(a.ambulanceId)}>
                    {String(a.ambulanceId)} — {a.driverName ?? "Unknown driver"}
                  </option>
                ))}
              </select>
            </PanelHead>
            <div className="h-[420px] md:h-[540px]">
              <ClientOnly
                fallback={
                  <div className="grid h-full place-items-center text-muted-foreground">
                    Loading map…
                  </div>
                }
              >
                <Suspense
                  fallback={
                    <div className="grid h-full place-items-center text-muted-foreground">
                      Loading map…
                    </div>
                  }
                >
                  <LiveMap
                    ambulances={d.ambulances}
                    signals={d.signals}
                    hospitals={d.hospitals}
                    selectedId={selected}
                  />
                </Suspense>
              </ClientOnly>
            </div>
            <div className="flex flex-wrap gap-4 border-t px-5 py-3 text-xs text-muted-foreground">
              <span className="flex items-center gap-2">
                <span className="legend pin-ambulance" /> Ambulance
              </span>
              <span className="flex items-center gap-2">
                <span className="legend pin-hospital" /> Hospital
              </span>
              <span className="flex items-center gap-2">
                <Dot tone="danger" />
                <Dot tone="warning" />
                <Dot tone="success" /> Signal state
              </span>
            </div>
          </div>
          <ActivityLog log={d.log} />
        </section>

        <section className="panel">
          <PanelHead
            title="Ambulance Monitoring"
            sub={`${d.ambulances.length} unit(s) reported by backend`}
          />
          {d.loading ? (
            <Empty text="Loading ambulances…" />
          ) : !d.ambulances.length ? (
            <Empty
              text={
                d.backend === "connected"
                  ? "Backend returned no ambulances."
                  : "No data — backend unavailable."
              }
            />
          ) : (
            <div className="grid gap-4 p-5 md:grid-cols-2 2xl:grid-cols-3">
              {d.ambulances.map((a) => (
                <AmbulanceCard
                  key={String(a.ambulanceId)}
                  a={a}
                  selected={selected === String(a.ambulanceId)}
                  onSelect={() => setSelected(String(a.ambulanceId))}
                />
              ))}
            </div>
          )}
        </section>

        <section className="panel">
          <PanelHead title="Traffic Signal Monitoring" sub={`${d.signals.length} signal(s)`} />
          {d.loading ? (
            <Empty text="Loading signals…" />
          ) : !d.signals.length ? (
            <Empty
              text={
                d.backend === "connected"
                  ? "Backend returned no signals."
                  : "No data — backend unavailable."
              }
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-muted text-left text-xs uppercase tracking-wider text-muted-foreground">
                  <tr>
                    <th className="px-5 py-3">Signal ID</th>
                    <th className="px-5 py-3">Name</th>
                    <th className="px-5 py-3">State</th>
                    <th className="px-5 py-3">Priority</th>
                    <th className="px-5 py-3">Ambulance</th>
                  </tr>
                </thead>
                <tbody>
                  {d.signals.map((s) => {
                    const st = sigState(s);
                    const p = sigPriority(s);
                    const amb = sigAmbulance(s);
                    return (
                      <tr key={sigId(s)} className="border-t">
                        <td className="px-5 py-3 font-mono">{sigId(s)}</td>
                        <td className="px-5 py-3 font-medium">{sigName(s)}</td>
                        <td className="px-5 py-3">
                          <TrafficLight state={st} />
                        </td>
                        <td className="px-5 py-3">
                          {p ? (
                            <Badge tone="success">Priority active</Badge>
                          ) : (
                            <Badge tone="muted">Normal</Badge>
                          )}
                        </td>
                        <td className="px-5 py-3 font-mono">{amb != null ? String(amb) : "—"}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <section className="panel">
          <PanelHead title="Hospital Monitoring" sub={`${d.hospitals.length} hospital(s)`} />
          {d.loading ? (
            <Empty text="Loading hospitals…" />
          ) : !d.hospitals.length ? (
            <Empty
              text={
                d.received.hospitals
                  ? "Backend returned no hospitals."
                  : "No data — hospital API unavailable."
              }
            />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-muted text-left text-xs uppercase tracking-wider text-muted-foreground">
                  <tr>
                    <th className="px-5 py-3">Hospital ID</th>
                    <th className="px-5 py-3">Name</th>
                    <th className="px-5 py-3">Address</th>
                    <th className="px-5 py-3">Contact</th>
                  </tr>
                </thead>
                <tbody>
                  {d.hospitals.map((h) => (
                    <tr key={String(h.hospitalId)} className="border-t">
                      <td className="px-5 py-3 font-mono">{String(h.hospitalId ?? "—")}</td>
                      <td className="px-5 py-3 font-medium">{hospName(h)}</td>
                      <td className="px-5 py-3">{h.address ?? "—"}</td>
                      <td className="px-5 py-3 font-mono">{h.contactNumber ?? "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <footer className="pb-4 text-center text-xs text-muted-foreground">
          Final-year engineering project · Live records from Spring Boot.
        </footer>
      </main>
    </div>
  );
}

function PanelHead({
  title,
  sub,
  children,
}: {
  title: string;
  sub?: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-b px-5 py-4">
      <div>
        <h2 className="font-display text-base font-semibold">{title}</h2>
        {sub && <p className="text-xs text-muted-foreground">{sub}</p>}
      </div>
      {children}
    </div>
  );
}
function Empty({ text }: { text: string }) {
  return <div className="px-5 py-12 text-center text-sm text-muted-foreground">{text}</div>;
}

function StatCard({
  label,
  value,
  tone,
  loading,
  empty,
}: {
  label: string;
  value: number;
  tone: string;
  loading: boolean;
  empty: boolean;
}) {
  return (
    <div className={`panel stat-card stat-${tone} p-5`}>
      <div className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
        {label}
      </div>
      <div className="mt-2 font-display text-3xl font-bold tabular-nums md:text-4xl">
        {loading ? "…" : empty ? "—" : value}
      </div>
    </div>
  );
}

function TrafficLight({ state }: { state: string }) {
  const t = stateTone(state);
  return (
    <span className="inline-flex items-center gap-3">
      <span className="traffic-light">
        <span className={`lamp tone-danger ${t === "danger" ? "on" : ""}`} />
        <span className={`lamp tone-warning ${t === "warning" ? "on" : ""}`} />
        <span className={`lamp tone-success ${t === "success" ? "on" : ""}`} />
      </span>
      <span className="font-mono text-xs font-semibold">{state}</span>
    </span>
  );
}

function AmbulanceCard({
  a,
  selected,
  onSelect,
}: {
  a: Ambulance;
  selected: boolean;
  onSelect: () => void;
}) {
  const progress =
    typeof a.routeProgress === "number"
      ? Math.max(0, Math.min(100, a.routeProgress <= 1 ? a.routeProgress * 100 : a.routeProgress))
      : null;
  return (
    <div
      role="button"
      tabIndex={0}
      onClick={onSelect}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onSelect();
        }
      }}
      className={`amb-card text-left ${selected ? "is-selected" : ""}`}
    >
      <div className="flex items-start justify-between gap-2">
        <div>
          <div className="font-mono text-sm font-bold">{String(a.ambulanceId)}</div>
          <div className="text-sm text-muted-foreground">
            {a.driverName ?? "Driver not provided"}
          </div>
        </div>
        <Badge tone={emergencyTone(a.emergencyStatus)}>{a.emergencyStatus ?? "UNKNOWN"}</Badge>
      </div>
      <div className="mt-3 text-sm">
        <span className="text-muted-foreground">→ </span>
        <span className="font-medium">{a.destinationHospital ?? "No destination"}</span>
      </div>
      <dl className="mt-3 grid grid-cols-3 gap-2 text-xs">
        <Metric k="Speed" v={fmt(a.currentSpeed, " km/h")} />
        <Metric k="Remaining" v={fmt(a.remainingDistance, " km", 2)} />
        <Metric k="ETA" v={fmt(a.etaMinutes, " min")} />
      </dl>
      <div className="mt-3">
        <div className="mb-1 flex justify-between text-xs text-muted-foreground">
          <span>Route progress</span>
          <span>{progress != null ? `${progress.toFixed(0)}%` : "—"}</span>
        </div>
        <div className="h-2 overflow-hidden rounded-full bg-muted">
          <div
            className="progress-fill h-full rounded-full"
            style={{ width: `${progress ?? 0}%` }}
          />
        </div>
      </div>
      <div className="mt-3 flex justify-between font-mono text-[11px] text-muted-foreground">
        <span>
          {fmt(a.currentLatitude, "", 5)}, {fmt(a.currentLongitude, "", 5)}
        </span>
        <span>
          {a.lastLocationUpdate ? new Date(a.lastLocationUpdate).toLocaleTimeString() : ""}
        </span>
      </div>
    </div>
  );
}
function Metric({ k, v }: { k: string; v: string }) {
  return (
    <div className="rounded-lg bg-muted px-2 py-1.5">
      <dt className="text-muted-foreground">{k}</dt>
      <dd className="font-semibold tabular-nums">{v}</dd>
    </div>
  );
}

function ActivityLog({ log }: { log: LogEntry[] }) {
  return (
    <div className="panel flex max-h-[640px] flex-col">
      <PanelHead title="System Activity Log" sub="Live events" />
      <ul className="flex-1 divide-y overflow-y-auto">
        {!log.length && (
          <li>
            <Empty text="Waiting for events…" />
          </li>
        )}
        {log.map((e) => (
          <li key={e.id} className="flex gap-3 px-5 py-2.5 text-sm">
            <Dot tone={e.type === "info" ? "muted" : e.type} />
            <div className="min-w-0 flex-1">
              <div className="break-words">{e.message}</div>
              <div className="font-mono text-[11px] text-muted-foreground">
                {e.time.toLocaleTimeString()}
              </div>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
