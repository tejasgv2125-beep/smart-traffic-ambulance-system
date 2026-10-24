import { afterEach, describe, expect, it, vi } from "vitest";

afterEach(() => {
  vi.unstubAllEnvs();
  vi.resetModules();
});

describe("Local Spring Boot configuration", () => {
  it("defaults the API to localhost:8087", async () => {
    vi.stubEnv("VITE_API_BASE_URL", "");
    vi.resetModules();
    const { API_BASE } = await import("@/lib/traffic");
    expect(API_BASE).toBe("http://localhost:8087");
  });
  it("keeps WebSocket configuration independent of the API URL", async () => {
    vi.stubEnv("VITE_API_BASE_URL", "http://localhost:8087/");
    vi.stubEnv("VITE_WS_URL", "ws://localhost:8090/custom-ws");
    vi.resetModules();
    const { API_BASE, WS_URL } = await import("@/lib/traffic");
    expect(API_BASE).toBe("http://localhost:8087");
    expect(WS_URL).toBe("ws://localhost:8090/custom-ws");
  });
  it("uses the local STOMP default when the WebSocket setting is blank", async () => {
    vi.stubEnv("VITE_API_BASE_URL", "http://localhost:8087");
    vi.stubEnv("VITE_WS_URL", " ");
    vi.resetModules();
    const { WS_URL } = await import("@/lib/traffic");
    expect(WS_URL).toBe("ws://localhost:8087/ws");
  });
  it("retains the five-second polling default", async () => {
    vi.stubEnv("VITE_POLL_INTERVAL_MS", "");
    vi.resetModules();
    const { POLL_MS } = await import("@/lib/traffic");
    expect(POLL_MS).toBe(5000);
  });
});
