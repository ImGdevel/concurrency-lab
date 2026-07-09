import sys
import json
import time
import argparse
import statistics
from concurrent.futures import ThreadPoolExecutor, as_completed
from urllib import request as urlrequest
from urllib.error import URLError, HTTPError


def do_request(url, method, body_fn, idx):
    body = body_fn(idx) if body_fn else None
    data = json.dumps(body).encode() if body is not None else None
    req = urlrequest.Request(url, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    start = time.perf_counter()
    try:
        with urlrequest.urlopen(req, timeout=10) as resp:
            raw = resp.read()
            elapsed = time.perf_counter() - start
            try:
                parsed = json.loads(raw)
            except Exception:
                parsed = raw.decode(errors="replace")
            return elapsed, resp.status, parsed
    except HTTPError as e:
        elapsed = time.perf_counter() - start
        try:
            parsed = json.loads(e.read())
        except Exception:
            parsed = None
        return elapsed, e.code, parsed
    except URLError as e:
        elapsed = time.perf_counter() - start
        return elapsed, -1, str(e)


def run(url, method, concurrency, total, body_fn=None, label=""):
    latencies = []
    results = []
    wall_start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = [pool.submit(do_request, url, method, body_fn, i) for i in range(total)]
        for f in as_completed(futures):
            elapsed, status, parsed = f.result()
            latencies.append(elapsed)
            results.append((status, parsed))
    wall = time.perf_counter() - wall_start

    latencies_ms = sorted(l * 1000 for l in latencies)
    n = len(latencies_ms)
    p50 = latencies_ms[int(n * 0.50)]
    p95 = latencies_ms[min(int(n * 0.95), n - 1)]
    p99 = latencies_ms[min(int(n * 0.99), n - 1)]
    avg = statistics.mean(latencies_ms)
    tps = total / wall if wall > 0 else float("inf")

    print(f"\n=== {label} ===")
    print(f"requests={total} concurrency={concurrency} wall={wall:.3f}s TPS={tps:.1f}")
    print(f"latency(ms) avg={avg:.2f} p50={p50:.2f} p95={p95:.2f} p99={p99:.2f} max={latencies_ms[-1]:.2f}")

    return {"results": results, "wall": wall, "tps": tps, "avg": avg, "p50": p50, "p95": p95, "p99": p99}


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", required=True)
    ap.add_argument("--method", default="POST")
    ap.add_argument("--concurrency", type=int, default=50)
    ap.add_argument("--total", type=int, default=500)
    ap.add_argument("--body-user-prefix", default=None, help="if set, sends {\"userId\": prefix+idx}")
    ap.add_argument("--label", default="bench")
    args = ap.parse_args()

    body_fn = None
    if args.body_user_prefix is not None:
        body_fn = lambda i: {"userId": f"{args.body_user_prefix}{i}"}

    summary = run(args.url, args.method, args.concurrency, args.total, body_fn, args.label)

    statuses = {}
    messages = {}
    for status, parsed in summary["results"]:
        statuses[status] = statuses.get(status, 0) + 1
        if isinstance(parsed, dict) and "message" in parsed:
            messages[parsed["message"]] = messages.get(parsed["message"], 0) + 1
    print(f"status_codes={statuses}")
    if messages:
        print(f"messages={messages}")
