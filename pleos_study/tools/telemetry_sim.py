#!/usr/bin/env python3
"""Fake Assetto Corsa telemetry for the Pleos dashboard.

Simulates a car lapping Inje Speedium (or Monza) and streams one JSON object per line
over TCP. Field names follow Assetto Corsa's own (RTCarInfo / shared memory), so a
real AC bridge can later emit the exact same lines and the app stays unchanged.

    python3 tools/telemetry_sim.py            # Inje Speedium, listens on 127.0.0.1:9000
    python3 tools/telemetry_sim.py --track monza
    # Android emulator reaches the host at 10.0.2.2:9000

Mirroring speed, pedals and steering onto the Pleos built-in cluster and driving view goes
through the emulator's "Pleos Connect Virtual Console" (port 8088 inside the guest):

    adb forward tcp:18088 tcp:8088
    python3 tools/telemetry_sim.py --pleos-console http://127.0.0.1:18088

With real Assetto Corsa on a Windows PC running SimHub and its Property Server plugin, pass
its live data on instead of simulating (same output, same mirroring; see tools/simhub_source.py):

    python3 tools/telemetry_sim.py --simhub <PC IP> --pleos-console http://127.0.0.1:18088
"""

import argparse
import json
import math
import os
import random
import socket
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

# `python3 -I` leaves this script's folder off the import path; the SimHub module sits beside it
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from simhub_source import SimHubSource  # noqa: E402

MAX_RPM = 8500
IDLE_RPM = 1100
UPSHIFT_RPM = 7900
DOWNSHIFT_RPM = 4300
# Top speed (km/h) of each gear at MAX_RPM
GEAR_TOP_KMH = [88, 128, 168, 208, 252, 305]
MAX_FUEL = 60.0
BRAKE_DECEL = 11.0  # m/s^2
LATERAL_GRIP = 15.5  # m/s^2, about 1.6 g sustained for a GT3 car
STEP = 5.0  # metres between track points
WHEELBASE = 2.7  # m
STEERING_RATIO = 14.0  # steering wheel degrees per road wheel degree
MAX_STEER_DEG = 540.0  # lock to lock is 3 turns

# Monza as (kind, length m, turns): heading changes in degrees (+ = right) spread across a
# segment. Added up they make one clockwise loop.
MONZA = [
    ("straight", 1050, []), ("corner", 120, [55, -55]),       # Rettifilo
    ("straight", 450, []), ("corner", 180, [80]),             # Curva Grande
    ("straight", 600, []), ("corner", 140, [-45, 45]),        # Roggia
    ("straight", 350, []), ("corner", 160, [100]),            # Lesmo 1
    ("straight", 200, []), ("corner", 130, [50]),             # Lesmo 2
    ("straight", 900, []), ("corner", 170, [-35, 50, -30]),   # Ascari
    ("straight", 1000, []), ("corner", 230, [145]),           # Parabolica
]

# Inje Speedium (Gangwon-do): 4,207 m, 20 corners, clockwise. Centre line traced from the
# official circuit map (image pixels, x east, y south) in corner order, starting at the finish
# line on the diagonal main straight; the track is a smooth closed curve through these points,
# scaled to the real length.
INJE_WAYPOINTS = [
    (520, 300), (615, 205), (700, 125), (740, 110),                          # main straight, T1
    (800, 107), (835, 118), (855, 150), (862, 185),                          # T2, T3
    (845, 250), (820, 320), (803, 347), (782, 346), (774, 325),              # T4 hairpin
    (783, 270), (795, 222), (798, 196), (780, 178), (750, 180),              # T5
    (718, 203), (702, 240), (712, 300), (722, 363),                          # T6, T7
    (700, 396), (640, 452), (578, 512), (552, 521), (520, 506),              # T8
    (470, 493), (420, 500), (352, 521), (331, 546),                          # T9, T10
    (323, 588), (300, 606), (278, 597), (262, 566), (236, 556),              # T11, T12
    (214, 568), (190, 583), (164, 575), (100, 512), (92, 480),               # T13, T14
    (124, 432), (150, 400), (155, 330), (150, 272), (115, 220),              # T15, T16
    (80, 172), (84, 131), (120, 124), (170, 155), (185, 195),                # T17, T18
    (185, 300), (185, 420), (192, 462), (222, 481), (280, 505), (306, 500),  # T19, T20
    (410, 402),                                                              # main straight
]


def path_from_segments(segments):
    """Points every STEP metres from a turn list; the closing error is spread over the lap."""
    x = z = heading = 0.0
    pts = [(x, z)]
    for _, length, turns in segments:
        n = max(1, round(length / STEP))
        ds = length / n
        for i in range(n):
            if turns:
                part = min(len(turns) - 1, i * len(turns) // n)
                heading += math.radians(turns[part]) * len(turns) / n
            x += ds * math.sin(heading)
            z -= ds * math.cos(heading)
            pts.append((x, z))
    ex, ez = pts[-1]
    m = len(pts) - 1
    return [(px - ex * i / m, pz - ez * i / m) for i, (px, pz) in enumerate(pts)][:-1]


def path_from_waypoints(waypoints, length, samples=20):
    """A closed Catmull-Rom curve through the waypoints, resampled every STEP metres."""
    n = len(waypoints)
    curve = []
    for i in range(n):
        p0, p1, p2, p3 = waypoints[i - 1], waypoints[i], waypoints[(i + 1) % n], waypoints[(i + 2) % n]
        for k in range(samples):
            t = k / samples
            curve.append(tuple(
                0.5 * (2 * p1[j] + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t * t
                       + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t ** 3)
                for j in (0, 1)))
    seg = [math.dist(curve[i], curve[(i + 1) % len(curve)]) for i in range(len(curve))]
    scale = length / sum(seg)
    curve = [(x * scale, z * scale) for x, z in curve]
    seg = [s * scale for s in seg]
    pts, acc, i, target = [], 0.0, 0, 0.0
    while target < length - 1e-6:
        while acc + seg[i] < target:
            acc += seg[i]
            i += 1
        f = (target - acc) / seg[i]
        a, b = curve[i], curve[(i + 1) % len(curve)]
        pts.append((a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f))
        target += STEP
    return pts


def corner_radii(pts, span=8):
    """Radius of the circle through each point and its neighbours `span` steps (40 m) away;
    the wide span keeps small kinks in a traced line from reading as extra corners."""
    n = len(pts)
    radii = []
    for i in range(n):
        a, b, c = pts[i - span], pts[i], pts[(i + span) % n]
        cross = abs((b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]))
        radii.append(1e9 if cross < 1e-6 else math.dist(a, b) * math.dist(b, c) * math.dist(c, a) / (2 * cross))
    return radii


def turn_sides(pts, span=8):
    """+1 where the track bends right, -1 where it bends left (x east, z south, seen from above)."""
    n = len(pts)
    sides = []
    for i in range(n):
        a, b, c = pts[i - span], pts[i], pts[(i + span) % n]
        sides.append(1 if (b[0] - a[0]) * (c[1] - b[1]) - (b[1] - a[1]) * (c[0] - b[0]) > 0 else -1)
    return sides


def speed_profile(radii):
    """Fastest speed at each point: grip-limited in corners, then braking room before them."""
    v = [min(305 / 3.6, math.sqrt(LATERAL_GRIP * r)) for r in radii]
    for _ in range(2):  # two passes so braking wraps over the finish line
        for i in range(len(v) - 1, -1, -1):
            v[i] = min(v[i], math.sqrt(v[(i + 1) % len(v)] ** 2 + 2 * BRAKE_DECEL * 0.9 * STEP))
    return v


class Track:
    def __init__(self, name, points):
        self.name = name
        self.points = points
        self.length = len(points) * STEP
        self.radii = corner_radii(points)
        self.speeds = speed_profile(self.radii)
        # Steering wheel angle that holds the line at each point: bicycle model, + = right
        self.steer = [side * STEERING_RATIO * math.degrees(math.atan(WHEELBASE / r))
                      for side, r in zip(turn_sides(points), self.radii)]


TRACKS = {
    "inje": lambda: Track("Inje Speedium", path_from_waypoints(INJE_WAYPOINTS, 4207)),
    "monza": lambda: Track("Monza", path_from_segments(MONZA)),
}


class Car:
    def __init__(self, track):
        self.track = track
        self.v = 0.0  # m/s
        self.gear = 1  # 1-based forward gear
        self.s = 0.0  # metres from the finish line
        self.gas = 0.0
        self.brake = 0.0
        self.shift_cut = 0.0
        self.lap_ms = 0.0
        self.last_lap = 0
        self.best_lap = 0
        self.lap_count = 0
        self.fuel = MAX_FUEL * 0.8
        self.tyres = [72.0, 72.0, 70.0, 70.0]
        self.wear = [92.0, 95.0, 88.0, 90.0]  # tread left, % (a used set)
        self.brakes = [180.0, 180.0, 150.0, 150.0]  # disc temperature, °C
        self.steer = 0.0  # steering wheel degrees, + = right
        self.pace = self._roll_pace()

    @staticmethod
    def _roll_pace():
        # How close to the limit the driver gets this lap
        return random.uniform(0.95, 1.0)

    @property
    def kmh(self):
        return self.v * 3.6

    @property
    def rpm(self):
        return max(IDLE_RPM, min(MAX_RPM, self.kmh * MAX_RPM / GEAR_TOP_KMH[self.gear - 1]))

    def _index(self, ahead=0.0):
        return int((self.s + ahead) / STEP) % len(self.track.points)

    def step(self, dt):
        t = self.track
        target = min(t.speeds[self._index()], t.speeds[self._index(STEP)]) * self.pace
        # Turn in a few metres early and ease the wheel there, like a driver does
        wanted = t.steer[self._index(STEP)]
        self.steer += (wanted - self.steer) * min(1.0, dt * 6)
        if self.v > target + 0.5:
            self.gas, self.brake = 0.0, min(1.0, 0.35 + (self.v - target) / 6)
        elif self.v < target - 1.5:
            self.gas, self.brake = 1.0, 0.0
        else:
            self.gas, self.brake = 0.5, 0.0

        # Longitudinal: power falls off with speed, drag grows with it
        accel = 0.0
        if self.shift_cut > 0:
            self.shift_cut -= dt
        else:
            accel += self.gas * 9.0 * (1 - (self.kmh / 320) ** 1.6)
        accel -= self.brake * BRAKE_DECEL
        accel -= 0.0004 * self.v ** 2
        self.v = max(0.0, self.v + accel * dt)

        if self.gas > 0.5 and self.rpm >= UPSHIFT_RPM and self.gear < len(GEAR_TOP_KMH):
            self.gear += 1
            self.shift_cut = 0.08
        elif self.rpm < DOWNSHIFT_RPM and self.gear > 1:
            self.gear -= 1

        self.s += self.v * dt
        self.lap_ms += dt * 1000
        if self.s >= t.length:
            self.s -= t.length
            self._finish_lap()

        self.fuel = max(0.0, self.fuel - (0.0006 + 0.0028 * self.gas * self.rpm / MAX_RPM) * dt)
        if self.fuel == 0.0:
            self.fuel = MAX_FUEL

        cornering = 1.0 if t.radii[self._index()] < 250 else 0.0
        front = self.brake * 6 + cornering * 1.6
        rear = self.gas * 1.3 + cornering * 1.0
        # A clockwise lap is mostly right-handers, so the left side works harder
        heat = [front * 1.08, front, rear * 1.06, rear]
        for i in range(4):
            self.tyres[i] += (heat[i] - (self.tyres[i] - 68) * 0.06) * dt
            # About 1% of tread a lap, more on the hard-worked corner; fresh set below 30%
            self.wear[i] -= (0.004 + 0.003 * heat[i]) * dt
            if self.wear[i] < 30:
                self.wear = [100.0] * 4
        # Discs heat with braking power and cool in the airflow; the rears do less of the work
        for i, share in enumerate((1.03, 1.0, 0.62, 0.6)):
            self.brakes[i] += (self.brake * self.v * 2.4 * share
                               - (self.brakes[i] - 40) * (0.03 + self.v * 0.0012)) * dt

    def _finish_lap(self):
        self.last_lap = int(self.lap_ms)
        if self.best_lap == 0 or self.last_lap < self.best_lap:
            self.best_lap = self.last_lap
        self.lap_count += 1
        self.lap_ms = 0.0
        self.pace = self._roll_pace()

    @property
    def lap_fraction(self):
        return min(self.s / self.track.length, 0.99999)

    def coordinates(self):
        pts = self.track.points
        f = self.s / STEP
        i = int(f) % len(pts)
        a, b = pts[i], pts[(i + 1) % len(pts)]
        t = f - int(f)
        return a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t

    def packet(self):
        x, z = self.coordinates()
        return {
            "carName": "Ferrari 488 GT3",
            "trackName": self.track.name,
            "speedKmh": round(self.kmh, 1),
            "engineRPM": round(self.rpm),
            "maxRpm": MAX_RPM,
            "gear": self.gear + 1,  # AC convention: 0 = R, 1 = N, 2 = 1st
            "gas": round(self.gas, 3),
            "brake": round(self.brake, 3),
            "steerDeg": round(max(-MAX_STEER_DEG, min(MAX_STEER_DEG, self.steer)), 1),
            "lapTime": int(self.lap_ms),
            "lastLap": self.last_lap,
            "bestLap": self.best_lap,
            "lapCount": self.lap_count,
            "fuel": round(self.fuel, 2),
            "maxFuel": MAX_FUEL,
            "tyreCoreTemp": [round(t, 1) for t in self.tyres],
            "tyreWear": [round(w, 1) for w in self.wear],  # % of tread left, 100 = new
            # Hot pressure in psi, as AC reports it: about 26.5 cold, rising with core temperature
            "wheelsPressure": [round(26.5 + (t - 70) * 0.08, 1) for t in self.tyres],
            "brakeTemp": [round(b, 1) for b in self.brakes],
            "tyreCompound": "Medium (M)",
            "normalizedCarPosition": round(self.lap_fraction, 5),
            "carCoordinates": [round(x, 2), 0.0, round(z, 2)],  # AC order: x, y (height), z
            # Rough stand-ins: TC cuts in on full-throttle exits in low gears, ABS under hard braking
            "isTcInAction": self.gas > 0.8 and self.gear <= 3 and self.kmh < 140,
            "isAbsInAction": self.brake > 0.9 and self.kmh > 60,
        }


class Broadcaster:
    def __init__(self, host, port):
        self.clients = []
        self.lock = threading.Lock()
        self.server = socket.create_server((host, port), reuse_port=False)
        threading.Thread(target=self._accept, daemon=True).start()

    def _accept(self):
        while True:
            conn, addr = self.server.accept()
            conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            print(f"client connected: {addr[0]}:{addr[1]}", flush=True)
            with self.lock:
                self.clients.append(conn)

    def send(self, line):
        data = (line + "\n").encode()
        with self.lock:
            for c in list(self.clients):
                try:
                    c.sendall(data)
                except OSError:
                    print("client disconnected", flush=True)
                    self.clients.remove(c)
                    c.close()


class PleosConsoleMirror:
    """Pushes speed, pedals and steering to the Pleos Virtual Console, so the built-in cluster
    follows along and the driving view's road bends through the circuit's corners.

    The console only accepts control writes while a "browser session" is alive, so this
    keeps one open with a heartbeat, the same way its own web page does.
    """

    SPEED_LIMIT_KPH = 240  # the console rejects anything above this
    PUSH_INTERVAL = 0.14  # matches the console page's own commit rate
    HEARTBEAT_INTERVAL = 1.0

    def __init__(self, base_url):
        self.base = base_url.rstrip("/")
        self.latest = None
        self.lock = threading.Lock()
        self.ok = None
        threading.Thread(target=self._run, daemon=True).start()

    def update(self, packet):
        with self.lock:
            self.latest = packet

    def _request(self, method, path, params):
        url = f"{self.base}{path}?{urllib.parse.urlencode(params)}"
        urllib.request.urlopen(urllib.request.Request(url, method=method), timeout=2).read()

    def _run(self):
        last_heartbeat = 0.0
        last_sent = None
        while True:
            try:
                now = time.monotonic()
                if now - last_heartbeat >= self.HEARTBEAT_INTERVAL:
                    first = last_heartbeat == 0.0
                    self._request("POST", "/browser/session", {"client": "ac-telemetry-sim"})
                    last_heartbeat = now
                    if first:
                        # The cluster only shows speed in Drive (it falls back to Park after a system restart)
                        self._request("PUT", "/vehicle/gear", {"gear": "Drive"})
                with self.lock:
                    p = self.latest
                if p is not None:
                    controls = {
                        "speed": min(self.SPEED_LIMIT_KPH, round(p["speedKmh"])),
                        "accelerator": round(p["gas"] * 100),
                        "brake": round(p["brake"] * 100),
                        "steering": round(p.get("steerDeg", 0)),  # the console's sign matches: + = right
                    }
                    if controls != last_sent:
                        self._request("PUT", "/vehicle/controls", controls)
                        last_sent = controls
                if self.ok is not True:
                    print(f"mirroring to Pleos cluster via {self.base}", flush=True)
                    self.ok = True
            except (urllib.error.URLError, OSError) as e:
                if self.ok is not False:
                    print(f"Pleos console unreachable ({e}); is `adb forward tcp:18088 tcp:8088` set?", flush=True)
                    self.ok = False
                last_heartbeat = 0.0
                last_sent = None
                time.sleep(1.0)
                continue
            time.sleep(self.PUSH_INTERVAL)


def pass_on_simhub(source, out, mirror, hz):
    """Sends SimHub's latest values at `hz`; nothing goes out while no game is being driven."""
    print(f"passing on SimHub telemetry from {source.host}:{source.port} at {hz} Hz", flush=True)
    while True:
        packet = source.packet()
        if packet is not None:
            out.send(json.dumps(packet, ensure_ascii=False, separators=(",", ":")))
            if mirror:
                mirror.update(packet)
        time.sleep(1 / hz)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=9000)
    parser.add_argument("--hz", type=int, default=30, help="packets per second")
    parser.add_argument("--track", choices=sorted(TRACKS), default="inje")
    parser.add_argument("--time-scale", type=float, default=1.0,
                        help="run the simulated car faster than real time (for testing)")
    parser.add_argument("--pleos-console", metavar="URL",
                        help="also drive the Pleos built-in cluster, e.g. http://127.0.0.1:18088")
    parser.add_argument("--simhub", metavar="HOST[:PORT]",
                        help="pass on live data from SimHub's Property Server (port 18082) instead of simulating")
    parser.add_argument("--steer-lock", type=float, default=540,
                        help="with --simhub: steering wheel lock to lock in degrees (GT3 cars are about 540)")
    args = parser.parse_args()

    out = Broadcaster(args.host, args.port)
    mirror = PleosConsoleMirror(args.pleos_console) if args.pleos_console else None
    if args.simhub:
        pass_on_simhub(SimHubSource(args.simhub, args.steer_lock), out, mirror, args.hz)
        return

    car = Car(TRACKS[args.track]())
    print(f"streaming fake AC telemetry ({car.track.name}) on {args.host}:{args.port} at {args.hz} Hz", flush=True)

    physics_dt = 1 / 120
    per_packet = max(1, round(120 / args.hz))
    next_tick = time.monotonic()
    while True:
        for _ in range(per_packet):
            car.step(physics_dt * args.time_scale)
        packet = car.packet()
        out.send(json.dumps(packet, separators=(",", ":")))
        if mirror:
            mirror.update(packet)
        next_tick += per_packet * physics_dt
        time.sleep(max(0.0, next_tick - time.monotonic()))


if __name__ == "__main__":
    main()
