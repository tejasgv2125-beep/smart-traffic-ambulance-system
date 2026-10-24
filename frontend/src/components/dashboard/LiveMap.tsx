import { useEffect, useRef, useState } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import {
  API_BASE,
  fmt,
  hasCoords,
  hospName,
  sigId,
  sigName,
  sigState,
  stateTone,
  type Ambulance,
  type Hospital,
  type Signal,
} from "@/lib/traffic";


type RoutePoint = {
  latitude: number;
  longitude: number;
};

type RouteResponse = {
  ambulanceId: string;
  hospitalId: string;
  hospitalName: string;
  distanceKm: number;
  durationMinutes: number;
  coordinates: RoutePoint[];
};

const esc = (s: unknown) => String(s ?? "—").replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);

const icon = (cls: string, html: string) =>
  L.divIcon({
    className: "",
    html: `<div class="map-pin ${cls}">${html}</div>`,
    iconSize: [30, 30],
    iconAnchor: [15, 15],
  });

export default function LiveMap({
  ambulances,
  signals,
  hospitals,
  selectedId,
}: {
  ambulances: Ambulance[];
  signals: Signal[];
  hospitals: Hospital[];
  selectedId: string | null;
}) {
  const el = useRef<HTMLDivElement>(null);
  const map = useRef<L.Map | null>(null);
  const layer = useRef<L.LayerGroup | null>(null);
  const markers = useRef<Map<string, L.Marker>>(new Map());
  const routeLayer = useRef<L.Polyline | null>(null);
  const fitted = useRef(false);
  const [routeInfo, setRouteInfo] = useState<RouteResponse | null>(null);
  const [routeError, setRouteError] = useState("");
  const [routeLoading, setRouteLoading] = useState(false);

  // Initialize map
  useEffect(() => {
    if (!el.current || map.current) return;

    map.current = L.map(el.current, { zoomControl: true }).setView([12.9716, 77.5946], 12);

    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution: "&copy; OpenStreetMap contributors",
    }).addTo(map.current);

    layer.current = L.layerGroup().addTo(map.current);

    return () => {
      map.current?.remove();
      map.current = null;
      layer.current = null;
      markers.current.clear();
      routeLayer.current = null;
    };
  }, []);

  // Draw ambulance, signal and hospital markers
  useEffect(() => {
    const m = map.current;
    const g = layer.current;

    if (!m || !g) return;

    g.clearLayers();
    markers.current.clear();

    const pts: L.LatLngExpression[] = [];

    signals.forEach((s) => {
      if (s.latitude == null || s.longitude == null || !hasCoords(s.latitude, s.longitude)) {
        return;
      }

      const st = sigState(s);

      L.marker([s.latitude, s.longitude], {
        icon: icon(`pin-signal tone-${stateTone(st)}`, "●"),
      })
        .bindPopup(`<b>${esc(sigName(s))}</b><br/>ID: ${esc(sigId(s))}<br/>State: ${esc(st)}`)
        .addTo(g);

      pts.push([s.latitude, s.longitude]);
    });

    hospitals.forEach((h) => {
      if (h.latitude == null || h.longitude == null || !hasCoords(h.latitude, h.longitude)) {
        return;
      }

      L.marker([h.latitude, h.longitude], {
        icon: icon("pin-hospital", "H"),
      })
        .bindPopup(
          `<b>${esc(hospName(h))}</b><br/>${esc(h.address)}<br/>Contact: ${esc(h.contactNumber)}`,
        )
        .addTo(g);

      pts.push([h.latitude, h.longitude]);
    });

    ambulances.forEach((a) => {
      if (
        a.currentLatitude == null ||
        a.currentLongitude == null ||
        !hasCoords(a.currentLatitude, a.currentLongitude)
      ) {
        return;
      }

      const id = String(a.ambulanceId);

      const mk = L.marker([a.currentLatitude, a.currentLongitude], {
        icon: icon(`pin-ambulance ${selectedId === id ? "is-selected" : ""}`, "✚"),
        zIndexOffset: 1000,
      })
        .bindTooltip(esc(id), {
          permanent: true,
          direction: "top",
          offset: [0, -14],
          className: "amb-label",
        })
        .bindPopup(
          `<b>Ambulance ${esc(id)}</b><br/>Driver: ${esc(a.driverName)}<br/>Status: ${esc(a.emergencyStatus)}<br/>` +
            `Destination: ${esc(a.destinationHospital)}<br/>Speed: ${fmt(a.currentSpeed, " km/h")}<br/>` +
            `Remaining: ${fmt(a.remainingDistance, " km", 2)}<br/>ETA: ${fmt(a.etaMinutes, " min")}`,
        )
        .addTo(g);

      markers.current.set(id, mk);
      pts.push([a.currentLatitude, a.currentLongitude]);
    });

    // When an ambulance is selected, the route effect below fits the map to
    // that ambulance's road route. Fitting every city-wide marker here zooms
    // too far out and makes signals look randomly placed.
    if (!fitted.current && !selectedId && pts.length) {
      m.fitBounds(L.latLngBounds(pts), {
        padding: [40, 40],
        maxZoom: 13,
      });
      fitted.current = true;
    }
  }, [ambulances, signals, hospitals, selectedId]);

  // Fetch and display the road route for the selected ambulance
  useEffect(() => {
    if (!selectedId) {
      routeLayer.current?.remove();
      routeLayer.current = null;
      setRouteInfo(null);
      setRouteError("");
      setRouteLoading(false);
      return;
    }

    let cancelled = false;

    const fetchRoute = async () => {
      setRouteLoading(true);
      setRouteError("");

      try {
        const response = await fetch(
          `${API_BASE}/api/routes/ambulance/${encodeURIComponent(selectedId)}`,
        );

        const data = await response.json();

        if (!response.ok) {
          throw new Error(data.error || `Route request failed (${response.status})`);
        }

        const route = data as RouteResponse;

        if (
          !Array.isArray(route.coordinates) ||
          route.coordinates.length < 2 ||
          !route.coordinates.every(
            (p) =>
              Number.isFinite(p.latitude) &&
              Number.isFinite(p.longitude) &&
              p.latitude >= -90 &&
              p.latitude <= 90 &&
              p.longitude >= -180 &&
              p.longitude <= 180,
          )
        ) {
          throw new Error("The route API returned invalid route coordinates.");
        }

        if (cancelled) return;

        routeLayer.current?.remove();

        const points = route.coordinates.map((p) => L.latLng(p.latitude, p.longitude));

        const currentMap = map.current;
        if (!currentMap) return;

        routeLayer.current = L.polyline(points, {
          color: "#2563eb",
          weight: 5,
          opacity: 0.85,
        }).addTo(currentMap);

        setRouteInfo(route);

        // Fit the map to the selected ambulance's road route
        currentMap.fitBounds(routeLayer.current.getBounds(), {
          padding: [45, 45],
          maxZoom: 16,
        });
      } catch (error) {
        if (cancelled) return;

        routeLayer.current?.remove();
        routeLayer.current = null;
        setRouteInfo(null);

        setRouteError(
          error instanceof Error ? error.message : "Unable to load the ambulance route.",
        );
      } finally {
        if (!cancelled) setRouteLoading(false);
      }
    };

    void fetchRoute();

    return () => {
      cancelled = true;
    };
  }, [selectedId]);

  // Route fetch fits the map to the selected ambulance route.

  return (
    <div className="relative h-full w-full">
      <div ref={el} className="h-full w-full" />

      {selectedId && (
        <div className="absolute left-3 top-3 z-[1000] max-w-xs rounded-lg bg-white p-3 text-sm shadow-md">
          <div className="font-semibold">Route for ambulance {esc(selectedId)}</div>

          {routeLoading && <div className="mt-1 text-gray-600">Calculating road route...</div>}

          {routeInfo && (
            <div className="mt-1 space-y-1">
              <div>Destination: {routeInfo.hospitalName}</div>
              <div>Distance: {Number(routeInfo.distanceKm).toFixed(2)} km</div>
              <div>Estimated travel time: {Number(routeInfo.durationMinutes).toFixed(0)} min</div>
            </div>
          )}

          {routeError && (
            <div className="mt-1 break-words text-red-600">Route unavailable: {routeError}</div>
          )}
        </div>
      )}
    </div>
  );
}
