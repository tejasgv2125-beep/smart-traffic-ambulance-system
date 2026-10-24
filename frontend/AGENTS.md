<!-- LOVABLE:BEGIN -->
> [!IMPORTANT]
> This project is connected to [Lovable](https://lovable.dev). Avoid rewriting
> published git history — force pushing, or rebasing/amending/squashing commits
> that are already pushed — as it rewrites history on Lovable's side and the
> user will likely lose their project history.
>
> Commits you push to the connected branch sync back to Lovable and show up in
> the editor, so keep the branch in a working state.
<!-- LOVABLE:END -->
- Dashboard data comes only from the user's Spring Boot backend (env-configured URLs); never add mock/fallback data.
- Keep traffic types, field readers, summary calculations, and connection diagnostics in the browser-safe traffic module so REST, STOMP, map and tests share the same API contract.
- Fetch Spring Boot directly in the browser using VITE_API_BASE_URL and VITE_WS_URL; do not introduce a hosted localhost proxy because it cannot reach the user's computer.
- Treat the REST connection as healthy only when all three endpoints succeed; preserve last received records and automatic polling on partial failures.
- Keep local Vite on port 8080 with strictPort enabled so the documented frontend origin stays stable; preserve the managed TanStack config and its sandbox overrides.
