# TODO

- [x] Evaluate restarting Cloudflare on failure (update KV registry) — watchdog restarts cloudflared and re-publishes stable URL
- [x] On main page of web server (`index.html`), show all connect URLs (LAN, tunnel, stable) so one can connect locally — mirror `RemotePlayUrls` / status dialog
- [x] Fix main page glitch — likely `index.html` polls `/api/playlists` every 5s and toggles `#content.busy` (opacity + pointer-events), causing a visible flicker on each refresh
- [x] In web server UI (`assets/remote/*.html`), show the chosen app icon instead of hardcoded “Stage Manager” text/titles — serve icon from `AppIconManager` selection (Default / Alt)
