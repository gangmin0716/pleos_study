"""Live telemetry from SimHub on the gaming PC, in the dashboard's JSON format.

SimHub reads Assetto Corsa (and most other sims) on the Windows PC. The free SimHub Property
Server plugin (github.com/pre-martin/SimHubPropertyServer, PropertyServer.dll in the SimHub
folder) exposes its properties over TCP, port 18082. This module subscribes to the ones the
dashboard needs and rebuilds the same JSON lines tools/telemetry_sim.py sends, so the app
can't tell where they came from. Used by `telemetry_sim.py --simhub <PC IP>`.

Known limits of the Property Server, accepted on purpose:
  * values change at most 10 times a second (the app is fed at 30 Hz with the latest values)
  * arrays aren't supported, so there are no world coordinates: the track map can't draw
"""

import re
import socket
import threading
import time

DEFAULT_PORT = 18082
CORNERS = ("FrontLeft", "FrontRight", "RearLeft", "RearRight")

# SimHub property -> how it's read. "dcp.gd.X" is DataCorePlugin.GameData.X (typed); names
# without a prefix are SimHub's generic properties, here AC's raw shared memory pages.
PROPERTIES = [
    "dcp.GameRunning", "dcp.GamePaused",
    "dcp.gd.CarModel", "dcp.gd.TrackName",
    "dcp.gd.SpeedKmh", "dcp.gd.Rpms", "dcp.gd.MaxRpm", "dcp.gd.Gear",
    "dcp.gd.Throttle", "dcp.gd.Brake",
    "dcp.gd.CurrentLapTime", "dcp.gd.LastLapTime", "dcp.gd.BestLapTime", "dcp.gd.CompletedLaps",
    "dcp.gd.Fuel", "dcp.gd.MaxFuel",
    "dcp.gd.TrackPositionPercent", "dcp.gd.TCActive", "dcp.gd.ABSActive",
    "dcp.gd.TyrePressureUnit",
    *(f"dcp.gd.TyreTemperature{c}" for c in CORNERS),
    *(f"dcp.gd.TyreWear{c}" for c in CORNERS),
    *(f"dcp.gd.TyrePressure{c}" for c in CORNERS),
    *(f"dcp.gd.BrakeTemperature{c}" for c in CORNERS),
    # Not in DataCorePlugin: straight from AC's shared memory
    "DataCorePlugin.GameRawData.Physics.SteerAngle",
    "DataCorePlugin.GameRawData.Graphics.TyreCompound",
]

PSI_PER = {"psi": 1.0, "bar": 14.5038, "kpa": 0.145038}
LINE = re.compile(r"^Property (\S+) (\S+) (.*)$")
TIMESPAN = re.compile(r"^(?:(\d+)\.)?(\d+):(\d+):(\d+)(?:\.(\d+))?$")


def parse_value(kind, raw):
    if raw == "(null)":
        return None
    if kind in ("integer", "long"):
        return int(raw)
    if kind == "double":
        return float(raw.replace(",", "."))
    if kind == "boolean":
        return raw == "True"
    if kind == "timespan":
        return timespan_ms(raw)
    return raw


def timespan_ms(raw):
    """.NET TimeSpan text ("00:01:23.4560000", "1.02:03:04") in milliseconds."""
    m = TIMESPAN.match(raw)
    if not m:
        return None
    days, h, mi, s, frac = m.groups()
    ms = ((int(days or 0) * 24 + int(h)) * 60 + int(mi)) * 60_000 + int(s) * 1000
    return ms + (int((frac or "0")[:3].ljust(3, "0")) if frac else 0)


def ac_gear(text):
    """SimHub's gear text ("R", "N", "1"…) in AC's numbering: 0 = R, 1 = N, 2 = 1st."""
    if text in (None, "", "N"):
        return 1
    if text == "R":
        return 0
    return int(text) + 1 if text.isdigit() else 1


class SimHubSource:
    """Keeps the latest value of every subscribed property; reconnects whenever SimHub drops."""

    def __init__(self, address, steer_lock):
        host, _, port = address.partition(":")
        self.host, self.port = host, int(port or DEFAULT_PORT)
        self.steer_lock = steer_lock
        self.values = {}
        self.connected = False
        threading.Thread(target=self._run, daemon=True).start()

    def _run(self):
        while True:
            try:
                with socket.create_connection((self.host, self.port), timeout=5) as conn:
                    conn.settimeout(None)
                    reader = conn.makefile("r", encoding="utf-8", newline="\n")
                    print(f"SimHub: {reader.readline().strip()} at {self.host}:{self.port}", flush=True)
                    conn.sendall("".join(f"subscribe {p}\r\n" for p in PROPERTIES).encode())
                    self.connected = True
                    for line in reader:
                        m = LINE.match(line.strip())
                        if m:
                            name, kind, raw = m.groups()
                            try:
                                self.values[name] = parse_value(kind, raw)
                            except ValueError:
                                pass
                print("SimHub closed the connection", flush=True)
            except OSError as e:
                print(f"SimHub unreachable ({e}); retrying", flush=True)
            self.connected = False
            self.values = {}
            time.sleep(2)

    def packet(self):
        """The current state as one dashboard line, or None while no game is being driven."""
        v = self.values
        if not self.connected or not v.get("dcp.GameRunning"):
            return None
        num = lambda name, default=0.0: v.get(name) if v.get(name) is not None else default
        def corners(prefix):
            # None until SimHub has sent any of the four, so the card shows "--" rather than zeros
            values = [v.get(f"dcp.gd.{prefix}{c}") for c in CORNERS]
            return None if all(x is None for x in values) else [x or 0.0 for x in values]

        to_psi = PSI_PER.get(str(v.get("dcp.gd.TyrePressureUnit", "psi")).lower(), 1.0)
        pressures = corners("TyrePressure")
        position = v.get("dcp.gd.TrackPositionPercent")
        if position is not None and position > 1:
            position /= 100  # some versions report 0..100
        return {
            "carName": v.get("dcp.gd.CarModel"),
            "trackName": v.get("dcp.gd.TrackName"),
            "speedKmh": round(num("dcp.gd.SpeedKmh"), 1),
            "engineRPM": round(num("dcp.gd.Rpms")),
            "maxRpm": v.get("dcp.gd.MaxRpm") or None,
            "gear": ac_gear(v.get("dcp.gd.Gear")),
            # SimHub reports pedals in percent
            "gas": round(num("dcp.gd.Throttle") / 100, 3),
            "brake": round(num("dcp.gd.Brake") / 100, 3),
            # AC's raw steerAngle is the normalised input (-1..1); the wheel turns half the lock each way
            "steerDeg": round(num("DataCorePlugin.GameRawData.Physics.SteerAngle") * self.steer_lock / 2, 1),
            "lapTime": num("dcp.gd.CurrentLapTime", 0),
            "lastLap": num("dcp.gd.LastLapTime", 0),
            "bestLap": num("dcp.gd.BestLapTime", 0),
            "lapCount": num("dcp.gd.CompletedLaps", 0),
            "fuel": v.get("dcp.gd.Fuel"),
            "maxFuel": v.get("dcp.gd.MaxFuel") or None,
            "tyreCoreTemp": corners("TyreTemperature"),
            "tyreWear": corners("TyreWear"),
            "wheelsPressure": [round(p * to_psi, 1) for p in pressures] if pressures else None,
            "brakeTemp": corners("BrakeTemperature"),
            "tyreCompound": v.get("DataCorePlugin.GameRawData.Graphics.TyreCompound"),
            "normalizedCarPosition": position,
            "isTcInAction": bool(v.get("dcp.gd.TCActive")),
            "isAbsInAction": bool(v.get("dcp.gd.ABSActive")),
        }
