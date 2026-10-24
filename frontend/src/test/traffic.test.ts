import { afterEach, describe, expect, it, vi } from "vitest";
import {
  connectionHint,
  fetchJson,
  hospName,
  isActiveEmergency,
  sigAmbulance,
  sigPriority,
  sigState,
  summarizeTraffic,
} from "@/lib/traffic";

afterEach(() => vi.unstubAllGlobals());

describe("Exact Spring Boot API fields", () => {
  it("reads currentState rather than legacy aliases", () => {
    expect(sigState({ currentState: "GREEN", signalState: "RED" })).toBe("GREEN");
  });
  it("reads priorityStatus and does not treat INACTIVE as ACTIVE", () => {
    expect(sigPriority({ priorityStatus: "ACTIVE" })).toBe(true);
    expect(sigPriority({ priorityStatus: "INACTIVE", priorityActive: true })).toBe(false);
    expect(sigPriority({ priorityStatus: false })).toBe(false);
    expect(sigPriority({ priorityStatus: true })).toBe(true);
  });
  it("reads associatedAmbulanceId", () => {
    expect(sigAmbulance({ associatedAmbulanceId: "AMB-3", ambulanceId: "wrong" })).toBe("AMB-3");
  });
  it("reads hospitalName", () => {
    expect(hospName({ hospitalId: 4, hospitalName: "API hospital", name: "wrong" })).toBe(
      "API hospital",
    );
  });
  it("derives active emergencies from emergencyStatus without matching INACTIVE", () => {
    expect(isActiveEmergency({ ambulanceId: 1, emergencyStatus: "ACTIVE" })).toBe(true);
    expect(isActiveEmergency({ ambulanceId: 2, emergencyStatus: "INACTIVE" })).toBe(false);
  });
  it("calculates summaries from received records, not expected inventory", () => {
    expect(
      summarizeTraffic(
        [
          { ambulanceId: 1, emergencyStatus: "ACTIVE" },
          { ambulanceId: 2, emergencyStatus: "COMPLETED" },
        ],
        [
          { signalId: 1, priorityStatus: true },
          { signalId: 2, priorityStatus: "INACTIVE" },
        ],
      ),
    ).toEqual({ total: 2, active: 1, priorities: 1, completed: 1 });
    expect(summarizeTraffic([], [])).toEqual({ total: 0, active: 0, priorities: 0, completed: 0 });
  });
  it("passes actual response records through without inserting data", async () => {
    const rows = [{ hospitalId: 4, hospitalName: "Returned hospital" }];
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(JSON.stringify(rows))));
    expect(await fetchJson("/api/hospitals")).toEqual(rows);
  });
  it("rejects unexpected JSON instead of silently fabricating an empty list", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response(JSON.stringify({ error: "not records" }))),
    );
    await expect(fetchJson("/api/signals")).rejects.toThrow("did not return an array");
  });
  it("explains the HTTPS preview loopback restriction", () => {
    expect(connectionHint("http://localhost:8087", "https:")).toContain("viewing computer");
  });
});
