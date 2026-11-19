# XFCEController

An experimental Android TV companion for the XFCE desktop streaming setup.

## Current prototype
- LibVLC playback for an H.264 MPEG-TS UDP stream (default `udp://@:1234`).
- WebSocket connection to the Python input bridge (default port `8765`).
- Touchpad overlay, click/scroll buttons, settings, and physical keyboard forwarding.
- GitHub Actions debug APK workflow.

## Build
Push to `main` or run **Actions → Android APK → Run workflow**. The debug APK is uploaded as the `XFCEController-debug` artifact.

## Configure
On first launch, open the gear button and set:
- Phone LAN IP
- WebSocket port (default 8765)
- The same bridge token configured in `remote.py`
- Video URL (default `udp://@:1234`)

The phone-side stream sender must send MPEG-TS/H.264 to the TV on UDP port 1234. The Python WebSocket bridge must be running and reachable from the TV.

## Important
This is an initial prototype, not a verified release. It has not yet been compiled or tested on the Android 4.4.4 TV. The LibVLC native package's API-19 runtime compatibility and TV device decoding must be tested before relying on it. Do not expose the input bridge to the internet; keep it on your trusted LAN and use a fresh token.
