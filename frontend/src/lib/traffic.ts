// Shared, browser-safe types + API helpers for the Spring Boot backend.
export type Ambulance = {
  ambulanceId: string | number;
  driverName?: string | null;
  currentLatitude?: number | null;
  currentLongitude?: number | null;
  destinationHospital?: string | null;
  emergencyStatus?: string | boolean | null;
  currentSpeed?: number | null;
  remainingDistance?: number | null;
  etaMinutes?: number | null;
  routeProgress?: number | null;
  lastLocationUpdate?: string | null;
  assignedRoute?: unknown;
  [k: string]: unknown;
};

export type Signal = {
  signalId?: string | number;
  signalName?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  currentState?: string | null;
  normalState?: string | null;
  priorityStatus?: boolean | string | null;
  associatedAmbulanceId?: string | number | null;
  lastUpdateTime?: string | null;
  [k: string]: unknown;
};

export type Hospital = {
  hospitalId?: string | number;
  hospitalName?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  address?: string | null;
  contactNumber?: string | null;
  [k: string]: unknown;
};

export const API_BASE = (
  (import.meta.env["VITE_API_BASE_URL"] as string | undefined)?.trim() || "http://localhost:8087"
).replace(/\/+$/, "");
export const WS_URL =
  (import.meta.env["VITE_WS_URL"] as string | undefined)?.trim() ||
  API_BASE.replace(/^http/, "ws") + "/ws";
const configuredPoll = Number(import.meta.env["VITE_POLL_INTERVAL_MS"] ?? 5000);
export const POLL_MS =
  Number.isFinite(configuredPoll) && configuredPoll > 0 ? configuredPoll : 5000;

export function connectionHint(base: string, pageProtocol: string): string {
  try {
    const url = new URL(base);
    const local = ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname);
    if (local && pageProtocol === "https:")
      return "This HTTPS preview is targeting a loopback address. Browser requests target the viewing computer, not a remote Spring Boot server; browser security may block HTTP/local-network access. Configure an HTTPS tunnel URL or run the frontend locally.";
    if (pageProtocol === "https:" && url.protocol === "http:")
      return "An HTTPS preview cannot reliably call an HTTP API. Configure an HTTPS API URL.";
    return "Check that the API is reachable and allows the frontend's exact origin through CORS.";
  } catch {
    return "VITE_API_BASE_URL must be a valid HTTP or HTTPS URL.";
  }
}

export async function fetchJson<T>(path: string): Promise<T> {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), 6000);
  try {
    const res = await fetch(API_BASE + path, {
      signal: ctrl.signal,
      headers: { Accept: "application/json" },
    });
    if (!res.ok) throw new Error(`${path} responded ${res.status}`);
    const data = await res.json();
    const rows = Array.isArray(data) ? data : (data?.content ?? data?.data);
    if (!Array.isArray(rows)) throw new Error(`${path} did not return an array of records`);
    return rows as T;
  } catch (error) {
    if (error instanceof Error && error.name === "AbortError")
      throw new Error(`${path} timed out after 6 seconds`);
    if (error instanceof TypeError)
      throw new Error(
        `${path}: network request blocked or server unreachable (check URL, HTTPS and CORS)`,
      );
    throw error;
  } finally {
    clearTimeout(t);
  }
}

// Exact Spring Boot field names; missing values never become invented records.
export const sigId = (s: Signal) => String(s.signalId ?? "—");
export const sigName = (s: Signal) => s.signalName ?? `Signal ${sigId(s)}`;
export const sigState = (s: Signal) => String(s.currentState ?? "UNKNOWN").toUpperCase();
export const sigPriority = (s: Signal) => {
  const p = s.priorityStatus;
  return (
    p === true ||
    (typeof p === "string" &&
      /^(TRUE|ACTIVE|YES|HIGH|PRIORITY|PRIORITY_ACTIVE)$/.test(p.trim().toUpperCase()))
  );
};
export const sigAmbulance = (s: Signal) => s.associatedAmbulanceId ?? null;
export const hospName = (h: Hospital) => h.hospitalName ?? `Hospital ${h.hospitalId ?? ""}`;

export type StatusTone = "danger" | "warning" | "success" | "muted";
export function stateTone(state: string): StatusTone {
  if (/RED|STOP/.test(state)) return "danger";
  if (/YELLOW|AMBER/.test(state)) return "warning";
  if (/GREEN|GO/.test(state)) return "success";
  return "muted";
}
export function emergencyTone(status?: string | boolean | null): StatusTone {
  if (status === true) return "danger";
  if (status === false || status == null || status === "") return "muted";
  const s = String(status).trim().toUpperCase();
  if (/^(TRUE|ACTIVE|EMERGENCY|EN_ROUTE|ENROUTE|CRITICAL|ON_DUTY)$/.test(s)) return "danger";
  if (/^(FALSE|IDLE|AVAILABLE|STANDBY|INACTIVE)$/.test(s)) return "muted";
  if (/ARRIV|COMPLET|DONE/.test(s)) return "success";
  return "warning";
}
export const isActiveEmergency = (a: Ambulance) => emergencyTone(a.emergencyStatus) === "danger";
export const isCompleted = (a: Ambulance) => emergencyTone(a.emergencyStatus) === "success";

export const summarizeTraffic = (ambulances: Ambulance[], signals: Signal[]) => ({
  total: ambulances.length,
  active: ambulances.filter(isActiveEmergency).length,
  priorities: signals.filter(sigPriority).length,
  completed: ambulances.filter(isCompleted).length,
});

export const fmt = (v: number | null | undefined, unit = "", digits = 1) =>
  typeof v === "number" && Number.isFinite(v) ? `${v.toFixed(digits)}${unit}` : "—";
export const hasCoords = (lat?: number | null, lng?: number | null) =>
  typeof lat === "number" &&
  typeof lng === "number" &&
  Number.isFinite(lat) &&
  Number.isFinite(lng);
