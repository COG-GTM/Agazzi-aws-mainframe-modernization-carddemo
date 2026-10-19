#!/usr/bin/env python3
"""Post-switch smoke test (runbook T-0 step 0.9 and rollback R.6). Stdlib only.

Checks, in order:
  health       GET  /actuator/health                     -> status UP
  maintenance  GET  /actuator/maintenance                -> closedFiles == [] (OPENFIL done)
  signon       POST /api/v1/screens/cosgn00 (CC00)       -> no fieldErrors, nextScreen set
  cavw         POST /api/v1/screens/coactvw (CAVW)       -> account shown, no ERROR message
  ct02         POST /api/v1/screens/cotrn02 (CT02)       -> test transaction confirmed (--allow-write)
  posttran     CLI  run-job.sh POSTTRAN                   -> exit 0 (--posttran)

A 404/405 on a screen endpoint means the online module is not integrated yet: the check is
PENDING (accepted unless --strict, which T-0 uses). Credentials come from the environment:
CARDDEMO_SMOKE_USER / CARDDEMO_SMOKE_PASSWORD (HTTP basic + sign-on fields), never argv.
Exit: 0 = all PASS (or PENDING without --strict), 1 = any FAIL, 2 = usage.
"""
import argparse
import base64
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from http.cookiejar import CookieJar
from pathlib import Path

HERE = Path(__file__).resolve().parent


# One terminal session for the whole run: COMMAREA/session cookie kept between screens (CICS
# pseudo-conversation), as the SPA and k6 do. Basic auth requests are CSRF-exempt.
_OPENER = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))


def http(base, method, path, body=None, auth=None, timeout=15):
    req = urllib.request.Request(base.rstrip("/") + path, method=method)
    req.add_header("Accept", "application/json")
    if auth:
        req.add_header("Authorization", "Basic " + base64.b64encode(f"{auth[0]}:{auth[1]}".encode()).decode())
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        req.add_header("Content-Type", "application/json")
    try:
        with _OPENER.open(req, data=data, timeout=timeout) as r:
            raw = r.read()
            return r.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw) if raw else None
        except ValueError:
            return e.code, None


def screen(base, name, fields, auth, action="ENTER"):
    return http(base, "POST", f"/api/v1/screens/{name}", {"action": action, "fields": fields}, auth)


def errors(resp):
    if not isinstance(resp, dict):
        return ["no JSON body"]
    fe = resp.get("fieldErrors") or []
    if isinstance(fe, dict):
        fe = [{"field": k, "message": v} for k, v in fe.items()]
    out = [f"{e.get('field')}: {e.get('message')}" for e in fe]
    out += [m.get("text", "") for m in resp.get("messages") or [] if m.get("severity") == "ERROR"]
    return out


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--base-url", default=os.environ.get("CARDDEMO_BASE_URL", "http://localhost:8080"))
    p.add_argument("--account", default="00000000050", help="ACCTSID for CAVW / CT02 (seed account of --card)")
    p.add_argument("--card", default="0500024453765740", help="card of --account for CT02 (seed xref)")
    p.add_argument("--allow-write", action="store_true", help="CT02 writes one test transaction (TDESC SMOKE)")
    p.add_argument("--posttran", action="store_true", help="also run POSTTRAN via run-job.sh")
    p.add_argument("--strict", action="store_true", help="PENDING counts as FAIL (T-0 go/no-go)")
    p.add_argument("--report", type=Path, help="write JSON results here")
    p.add_argument("--wait", type=int, default=0, help="seconds to wait for health UP before checking")
    a = p.parse_args(argv)
    user = os.environ.get("CARDDEMO_SMOKE_USER")
    pwd = os.environ.get("CARDDEMO_SMOKE_PASSWORD")
    auth = (user, pwd) if user and pwd else None
    results = []

    def record(name, status, detail=""):
        results.append({"check": name, "status": status, "detail": detail})
        print(f"{status:8} {name:12} {detail}")

    deadline = time.time() + a.wait
    while True:
        try:
            code, body = http(a.base_url, "GET", "/actuator/health")
        except OSError as e:
            code, body = 0, {"error": str(e)}
        if (code == 200 and (body or {}).get("status") == "UP") or time.time() >= deadline:
            break
        time.sleep(3)
    up = code == 200 and (body or {}).get("status") == "UP"
    record("health", "PASS" if up else "FAIL", f"HTTP {code} {body}")
    if not up:
        return finish(results, a)

    code, body = http(a.base_url, "GET", "/actuator/maintenance", auth=auth)
    if code == 200:
        closed = (body or {}).get("closedFiles") or []
        record("maintenance", "PASS" if not closed else "FAIL", f"closedFiles={closed}")
    else:
        record("maintenance", "PENDING" if code in (401, 404) else "FAIL", f"HTTP {code} (MaintenanceModePort/B5 not exposed)")

    def screen_check(name, scr, fields, ok, first_send=False):
        try:
            if first_send:  # first SEND of the map (transaction start), then the terminal input
                http(a.base_url, "GET", f"/api/v1/screens/{scr}", auth=auth)
            code, body = screen(a.base_url, scr, fields, auth)
        except OSError as e:
            record(name, "FAIL", str(e))
            return
        if code in (404, 405):
            record(name, "PENDING", f"HTTP {code}: {scr} not integrated yet")
        elif code == 401 and not auth:
            record(name, "PENDING", "HTTP 401: set CARDDEMO_SMOKE_USER/CARDDEMO_SMOKE_PASSWORD")
        elif code != 200:
            record(name, "FAIL", f"HTTP {code} {body}")
        else:
            errs = errors(body)
            msg = ok(body)
            record(name, "FAIL" if errs or msg else "PASS", "; ".join(errs) or msg or f"screen={body.get('screen')}")

    screen_check("signon", "cosgn00", {"USERID": user or "", "PASSWD": pwd or ""},
                 lambda b: None if b.get("nextScreen") else "no nextScreen after sign-on")
    screen_check("cavw", "coactvw", {"ACCTSID": a.account},
                 lambda b: None if a.account.lstrip("0") in json.dumps(b.get("data")) else "account not in response data",
                 first_send=True)
    if a.allow_write:
        fields = {"ACTIDIN": a.account, "CARDNIN": a.card, "TTYPCD": "01", "TCATCD": "0001", "TRNSRC": "POS TERM",
                  "TDESC": "SMOKE TEST CUTOVER", "TRNAMT": "-00000001.00", "TORIGDT": time.strftime("%Y-%m-%d"),
                  "TPROCDT": time.strftime("%Y-%m-%d"), "MID": "999999999", "MNAME": "SMOKE", "MCITY": "SMOKE",
                  "MZIP": "00000", "CONFIRM": "Y"}
        screen_check("ct02", "cotrn02", fields,
                     lambda b: None if any("successfully" in (m.get("text") or "").lower() for m in b.get("messages") or [])
                     else "no confirmation message", first_send=True)
    else:
        record("ct02", "SKIPPED", "read-only run (pass --allow-write at T-0)")

    if a.posttran:
        r = subprocess.run([str(HERE / "run-job.sh"), "POSTTRAN"], capture_output=True, text=True)
        tail = (r.stdout + r.stderr).strip().splitlines()[-1:] or [""]
        record("posttran", "PASS" if r.returncode == 0 else ("PENDING" if r.returncode == 3 else "FAIL"),
               f"exit {r.returncode} {tail[0]}")
    else:
        record("posttran", "SKIPPED", "pass --posttran")
    return finish(results, a)


def finish(results, a):
    bad = {"FAIL"} | ({"PENDING"} if a.strict else set())
    ok = not any(r["status"] in bad for r in results)
    if a.report:
        a.report.parent.mkdir(parents=True, exist_ok=True)
        a.report.write_text(json.dumps({"ok": ok, "strict": a.strict, "baseUrl": a.base_url, "results": results}, indent=2))
    print("SMOKE TEST " + ("OK" if ok else "FAILED"))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
