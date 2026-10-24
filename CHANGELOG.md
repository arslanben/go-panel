# Changelog

## 1.0.0

- Initial release.
- Dark-only **Go Panel** app with a new gauge/terminal logo.
- 4x2 widget: large monthly used percentage, 5-hour/week/month rows, badges, reset countdowns, and colored bars.
- 4x1 compact widget: large monthly percentage and three mini rows.
- Used percentages instead of remaining percentages.
- Bar and badge colors: green below 50%, yellow 50–74%, red 75%+.
- Subscription end countdown in days in the widget header.
- Tap-to-refresh, a 10-minute inexact alarm while awake, and a 30-minute WorkManager backstop.
- Encrypted API key storage using the Android Keystore.
- API key guidance reflects the console's current permission check.
- English and Turkish UI.
- Security hardening: backup disabled, HTTPS-only, redirects disabled, no foreground service.
