#!/usr/bin/env python3
"""Shows what the Mac is playing (Apple Music, Spotify) on the emulator's music card.

The Android emulator is a separate device: it can't see media playing on the Mac it runs on,
and it can't pair with the Mac over Bluetooth. This bridge reads the Mac's player through
AppleScript and streams it to the app as JSON lines over TCP; the app sends transport commands
back on the same connection.

    python3 tools/mac_media_bridge.py            # listens on 127.0.0.1:9100
    # the emulator reaches the Mac at 10.0.2.2:9100 (debug builds connect automatically)

macOS asks once for permission to control Music / Spotify (Privacy & Security > Automation).

Lines sent to the app:
    {"source": "Music", "title", "artist", "album", "durationMs", "positionMs", "playing",
     "shuffle": bool, "repeat": "off" | "all" | "one", "artKey": str, "art"?: base64 JPEG}
    {"source": null}                              # nothing playing or paused on the Mac
"art" is sent only when the track changes (and to a newly connected app).

Lines the app sends:
    {"cmd": "playpause" | "next" | "previous" | "shuffle" | "repeat"}
"""

import argparse
import base64
import json
import os
import socket
import subprocess
import tempfile
import threading
import time
import urllib.parse
import urllib.request

SEP = "\x1f"  # field separator in AppleScript output; never appears in track names

# Each script returns "" when the player is stopped, otherwise one SEP-joined line. They only
# run once `is running` says the app is open: that keeps the bridge from launching a player, and
# a script naming an app that isn't installed doesn't even compile.
MUSIC_STATE = f'''
tell application "Music"
    if player state is stopped then return ""
    set t to current track
    return (player state as text) & "{SEP}" & (name of t) & "{SEP}" & (artist of t) & "{SEP}" & (album of t) & "{SEP}" & (duration of t) & "{SEP}" & (player position) & "{SEP}" & (shuffle enabled) & "{SEP}" & (song repeat as text)
end tell
'''

SPOTIFY_STATE = f'''
tell application "Spotify"
    if player state is stopped then return ""
    set t to current track
    return (player state as text) & "{SEP}" & (name of t) & "{SEP}" & (artist of t) & "{SEP}" & (album of t) & "{SEP}" & ((duration of t) / 1000) & "{SEP}" & (player position) & "{SEP}" & (shuffling) & "{SEP}" & (repeating) & "{SEP}" & (artwork url of t)
end tell
'''

# Music hands the artwork over as raw data, which has to be written out from AppleScript
MUSIC_ART = '''
on run argv
  tell application "Music"
    set t to current track
    if (count of artworks of t) is 0 then return "none"
    set d to raw data of artwork 1 of t
  end tell
  set f to open for access (POSIX file (item 1 of argv)) with write permission
  set eof of f to 0
  write d to f
  close access f
  return "ok"
end run
'''

COMMANDS = {
    "Music": {
        "playpause": 'tell application "Music" to playpause',
        "next": 'tell application "Music" to next track',
        "previous": 'tell application "Music" to previous track',
        "shuffle": 'tell application "Music" to set shuffle enabled to not shuffle enabled',
        # off -> all -> one -> off, the same order the app's button uses
        "repeat": 'tell application "Music"\n'
                  '  if song repeat is off then\n    set song repeat to all\n'
                  '  else if song repeat is all then\n    set song repeat to one\n'
                  '  else\n    set song repeat to off\n  end if\nend tell',
    },
    "Spotify": {
        "playpause": 'tell application "Spotify" to playpause',
        "next": 'tell application "Spotify" to next track',
        "previous": 'tell application "Spotify" to previous track',
        "shuffle": 'tell application "Spotify" to set shuffling to not shuffling',
        "repeat": 'tell application "Spotify" to set repeating to not repeating',
    },
}


def osascript(script, *args):
    result = subprocess.run(["osascript", "-e", script, *args], capture_output=True, text=True, timeout=5)
    if result.returncode != 0 and result.stderr.strip():
        raise RuntimeError(result.stderr.strip())
    return result.stdout.strip()


def read_player(name):
    try:
        if osascript(f'application "{name}" is running') != "true":
            return None
        out = osascript(MUSIC_STATE if name == "Music" else SPOTIFY_STATE)
    except (RuntimeError, subprocess.TimeoutExpired) as e:
        print(f"{name}: {e}", flush=True)
        return None
    if not out:
        return None
    f = out.split(SEP)
    if name == "Music":
        repeat = f[7] if f[7] in ("off", "all", "one") else "off"
        art_url = None
    else:
        repeat = "all" if f[7] == "true" else "off"
        art_url = f[8] or None
    return {
        "source": name,
        "playing": f[0] == "playing",
        "title": f[1],
        "artist": f[2],
        "album": f[3],
        "durationMs": int(float(f[4]) * 1000),
        "positionMs": int(float(f[5].replace(",", ".")) * 1000),
        "shuffle": f[6] == "true",
        "repeat": repeat,
        "_artUrl": art_url,
    }


def itunes_cover(artist, album):
    """Album cover URL from Apple's public iTunes Search API, or None."""
    query = urllib.parse.urlencode({"term": f"{artist} {album}", "entity": "album", "limit": 1})
    try:
        with urllib.request.urlopen(f"https://itunes.apple.com/search?{query}", timeout=5) as r:
            results = json.load(r).get("results", [])
    except (OSError, ValueError) as e:
        print(f"artwork lookup: {e}", flush=True)
        return None
    if not results or "artworkUrl100" not in results[0]:
        return None
    return results[0]["artworkUrl100"].replace("100x100bb", "600x600bb")


class Art:
    """Fetches the current track's artwork once per track, as base64 JPEG at most 512px."""

    def __init__(self):
        self.dir = tempfile.mkdtemp(prefix="mac-media-bridge-")
        self.key = None
        self.data = None

    def get(self, track):
        key = f"{track['source']}{SEP}{track['title']}{SEP}{track['artist']}{SEP}{track['album']}"
        if key == self.key:
            return key, self.data
        self.key, self.data = key, None
        raw = os.path.join(self.dir, "art.raw")
        try:
            url = track["_artUrl"]
            if track["source"] == "Music" and osascript(MUSIC_ART, raw) != "ok":
                # Streamed Apple Music tracks often carry no artwork: look the album up instead
                url = itunes_cover(track["artist"], track["album"])
            if url:
                if not url.startswith("https://"):
                    return key, None
                with urllib.request.urlopen(url, timeout=5) as r, open(raw, "wb") as out:
                    out.write(r.read())
            elif track["source"] != "Music":
                return key, None
            jpg = os.path.join(self.dir, "art.jpg")
            subprocess.run(["sips", "-Z", "512", "-s", "format", "jpeg", raw, "--out", jpg],
                           capture_output=True, check=True, timeout=10)
            with open(jpg, "rb") as f:
                self.data = base64.b64encode(f.read()).decode()
        except (OSError, RuntimeError, subprocess.SubprocessError) as e:
            print(f"artwork: {e}", flush=True)
        return key, self.data


class Bridge:
    def __init__(self, host, port):
        self.clients = []  # [socket, sent_art_key]
        self.lock = threading.Lock()
        self.source = None  # the player the app is looking at, for commands
        self.server = socket.create_server((host, port))
        threading.Thread(target=self._accept, daemon=True).start()

    def _accept(self):
        while True:
            conn, addr = self.server.accept()
            print(f"app connected: {addr[0]}:{addr[1]}", flush=True)
            with self.lock:
                self.clients.append([conn, None])
            threading.Thread(target=self._read_commands, args=(conn,), daemon=True).start()

    def _read_commands(self, conn):
        try:
            for line in conn.makefile("r", encoding="utf-8"):
                try:
                    cmd = json.loads(line).get("cmd")
                except json.JSONDecodeError:
                    continue
                script = COMMANDS.get(self.source or "", {}).get(cmd)
                if script:
                    print(f"{self.source}: {cmd}", flush=True)
                    try:
                        osascript(script)
                    except (RuntimeError, subprocess.TimeoutExpired) as e:
                        print(f"{self.source} {cmd}: {e}", flush=True)
        except OSError:
            pass

    def send(self, track, art_key, art):
        with self.lock:
            for entry in list(self.clients):
                conn, sent_key = entry
                msg = dict(track) if track else {"source": None}
                msg.pop("_artUrl", None)
                if track:
                    msg["artKey"] = art_key
                    if art and sent_key != art_key:
                        msg["art"] = art
                        entry[1] = art_key
                try:
                    conn.sendall((json.dumps(msg, ensure_ascii=False) + "\n").encode())
                except OSError:
                    print("app disconnected", flush=True)
                    self.clients.remove(entry)
                    conn.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=9100)
    parser.add_argument("--interval", type=float, default=1.0, help="seconds between polls")
    args = parser.parse_args()

    bridge = Bridge(args.host, args.port)
    art = Art()
    print(f"sharing the Mac's Music / Spotify on {args.host}:{args.port}", flush=True)
    last = None
    while True:
        players = [p for p in (read_player("Music"), read_player("Spotify")) if p]
        # A playing player wins; otherwise the one the app was already showing, else any paused one
        track = next((p for p in players if p["playing"]), None) \
            or next((p for p in players if p["source"] == bridge.source), None) \
            or (players[0] if players else None)
        bridge.source = track["source"] if track else None
        art_key, art_data = art.get(track) if track else (None, None)
        bridge.send(track, art_key, art_data)
        now = (track or {}).get("title"), (track or {}).get("playing")
        if now != last:
            print(f"now: {track['source']} · {track['title']} · {'playing' if track['playing'] else 'paused'}"
                  if track else "now: nothing", flush=True)
            last = now
        time.sleep(args.interval)


if __name__ == "__main__":
    main()
