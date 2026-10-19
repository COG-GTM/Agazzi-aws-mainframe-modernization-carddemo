#!/usr/bin/env python3
"""Minimal contract stub (openapi.yaml shapes) for the smoke-test self-test. Paths under
/missing/ answer 404 like an app where the online modules are not integrated yet."""
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer


def screen(name, data=None, messages=None, next_screen=None):
    return {"screen": name, "data": data or {}, "messages": messages or [], "nextScreen": next_screen,
            "fieldErrors": {}, "context": {}}


class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def reply(self, code, body):
        raw = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        path = self.path.removeprefix("/missing")
        if path == "/actuator/health":
            self.reply(200, {"status": "UP"})
        elif path == "/actuator/maintenance" and not self.path.startswith("/missing"):
            self.reply(200, {"closedFiles": []})
        else:
            self.reply(404, {})

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        f = body.get("fields") or {}
        if self.path.startswith("/missing"):
            self.reply(404, {})
        elif self.path == "/api/v1/screens/cosgn00":
            self.reply(200, screen("COSGN0A", next_screen="COMEN1A"))
        elif self.path == "/api/v1/screens/coactvw":
            self.reply(200, screen("CACTVWA", data={"ACCTSID": f.get("ACCTSID"), "ACSTTUS": "Y"}))
        elif self.path == "/api/v1/screens/cotrn02":
            self.reply(200, screen("COTRN2A", messages=[{"severity": "INFO", "text": "Transaction added successfully. Your Tran ID is 0000000000000301."}]))
        else:
            self.reply(404, {})


HTTPServer(("127.0.0.1", int(sys.argv[1])), H).serve_forever()
