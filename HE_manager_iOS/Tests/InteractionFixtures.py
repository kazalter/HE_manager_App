"""Local synthetic HTTP responses for native interaction regressions; no real library access."""
import json
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

lock = threading.Lock()
state = {"scenario": "normal", "patches": [], "patch_done": 0, "media_requests": 0, "creator_requests": 0}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def send_json(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
        if self.path == "/control":
            with lock:
                state.update(scenario=body["scenario"], patches=[], patch_done=0, media_requests=0, creator_requests=0)
            self.send_json(200, {})
        else:
            self.send_json(401, {"detail": "bad credentials"})

    def do_GET(self):
        path = urlparse(self.path)
        if path.path == "/state":
            with lock:
                snapshot = dict(state)
            self.send_json(200, snapshot)
            return
        if self.headers.get("Authorization") == "Bearer expired":
            self.send_json(401, {"detail": "expired fixture session"})
            return
        with lock:
            scenario = state["scenario"]
        if path.path == "/tags":
            self.send_json(200, [])
        elif path.path == "/mobile/media":
            with lock:
                state["media_requests"] += 1
                number = state["media_requests"]
            if scenario == "media_fail":
                self.send_json(503, {"detail": "offline fixture"})
                return
            if scenario == "media_race":
                time.sleep(0.35 if number == 1 else 0.02)
            self.send_json(200, [{"id": number if scenario == "media_race" else 1, "title": "sample", "media_type": "manga", "favorite": False}])
        elif path.path == "/mobile/creators":
            kind = parse_qs(path.query).get("kind", ["all"])[0]
            with lock:
                state["creator_requests"] += 1
            time.sleep(0.35 if kind == "artist" else 0.02)
            self.send_json(200, [{"kind": kind, "key": kind + ":1", "display_name": kind}])
        else:
            self.send_json(404, {})

    def do_PATCH(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))))
        target = body["favorite"]
        with lock:
            state["patches"].append(target)
            number = len(state["patches"])
            scenario = state["scenario"]
        time.sleep(0.35 if target else 0.02)
        if scenario == "favorite_fail" and number == 2:
            self.send_json(503, {"detail": "favorite fixture failed"})
        else:
            self.send_json(200, {"id": 1, "title": "sample", "media_type": "manga", "favorite": target})
        with lock:
            state["patch_done"] += 1


server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
with open(sys.argv[1], "w", encoding="utf-8") as port_file:
    port_file.write(str(server.server_port))
server.serve_forever()
