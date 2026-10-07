#!/usr/bin/env python3
"""Local mock Anthropic + OpenAI upstreams for gateway demos.

  ANTHROPIC mock : http://127.0.0.1:18081
  OPENAI mock    : http://127.0.0.1:18082

Modes for Anthropic (env ANTHROPIC_MOCK_MODE):
  fail       - HTTP 503 (default) — triggers pre-stream failover
  stream     - successful Anthropic-shaped SSE
  hang       - accept connection but never send headers (TTFB timeout)
  drop       - send one content delta then close abruptly (mid-stream fail)

OpenAI mock always streams a short successful OpenAI-shaped SSE completion
unless OPENAI_MOCK_MODE=fail.
"""

from __future__ import annotations

import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


ANTHROPIC_PORT = int(os.environ.get("ANTHROPIC_MOCK_PORT", "18081"))
OPENAI_PORT = int(os.environ.get("OPENAI_MOCK_PORT", "18082"))
ANTHROPIC_MODE = os.environ.get("ANTHROPIC_MOCK_MODE", "fail").lower()
OPENAI_MODE = os.environ.get("OPENAI_MOCK_MODE", "stream").lower()


def _read_json(handler: BaseHTTPRequestHandler) -> dict:
    length = int(handler.headers.get("Content-Length", "0"))
    raw = handler.rfile.read(length) if length else b"{}"
    try:
        return json.loads(raw.decode("utf-8") or "{}")
    except json.JSONDecodeError:
        return {}


class AnthropicHandler(BaseHTTPRequestHandler):
    def log_message(self, fmt: str, *args) -> None:
        print(f"[anthropic-mock] {self.command} {self.path} -> {fmt % args}")

    def do_POST(self) -> None:
        if self.path.rstrip("/") != "/v1/messages":
            self.send_error(404, "not found")
            return
        _read_json(self)
        mode = ANTHROPIC_MODE

        if mode == "hang":
            time.sleep(120)
            return

        if mode == "fail":
            body = json.dumps({"type": "error", "error": {"type": "api_error", "message": "demo primary down"}}).encode()
            self.send_response(503)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        # stream or drop
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()

        events = [
            {"type": "message_start", "message": {"id": "msg_demo", "role": "assistant"}},
            {
                "type": "content_block_delta",
                "index": 0,
                "delta": {"type": "text_delta", "text": "Hello from Anthropic mock"},
            },
        ]
        for event in events:
            self.wfile.write(f"event: {event['type']}\ndata: {json.dumps(event)}\n\n".encode())
            self.wfile.flush()
            time.sleep(0.05)

        if mode == "drop":
            # Abrupt close after first token — mid-stream hard fail for the gateway.
            return

        end = {
            "type": "message_delta",
            "delta": {"stop_reason": "end_turn"},
            "usage": {"output_tokens": 5},
        }
        self.wfile.write(f"event: message_delta\ndata: {json.dumps(end)}\n\n".encode())
        self.wfile.write(b"event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n")
        self.wfile.flush()


class OpenAiHandler(BaseHTTPRequestHandler):
    def log_message(self, fmt: str, *args) -> None:
        print(f"[openai-mock] {self.command} {self.path} -> {fmt % args}")

    def do_POST(self) -> None:
        if self.path.rstrip("/") != "/chat/completions":
            self.send_error(404, "not found")
            return
        body = _read_json(self)
        stream = bool(body.get("stream"))

        if OPENAI_MODE == "fail":
            payload = json.dumps({"error": {"message": "demo openai down"}}).encode()
            self.send_response(503)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
            return

        if not stream:
            payload = {
                "id": "chatcmpl-demo",
                "object": "chat.completion",
                "model": body.get("model", "gpt-4o"),
                "choices": [
                    {
                        "index": 0,
                        "message": {"role": "assistant", "content": "Hello from OpenAI mock"},
                        "finish_reason": "stop",
                    }
                ],
            }
            raw = json.dumps(payload).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)
            return

        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        chunks = [
            {"id": "chatcmpl-demo", "object": "chat.completion.chunk", "model": "gpt-4o",
             "choices": [{"index": 0, "delta": {"role": "assistant", "content": "Hello"}, "finish_reason": None}]},
            {"id": "chatcmpl-demo", "object": "chat.completion.chunk", "model": "gpt-4o",
             "choices": [{"index": 0, "delta": {"content": " from OpenAI mock"}, "finish_reason": None}]},
            {"id": "chatcmpl-demo", "object": "chat.completion.chunk", "model": "gpt-4o",
             "choices": [{"index": 0, "delta": {}, "finish_reason": "stop"}]},
        ]
        for chunk in chunks:
            self.wfile.write(f"data: {json.dumps(chunk)}\n\n".encode())
            self.wfile.flush()
            time.sleep(0.05)
        self.wfile.write(b"data: [DONE]\n\n")
        self.wfile.flush()


def _serve(handler, port: int, name: str) -> None:
    server = ThreadingHTTPServer(("127.0.0.1", port), handler)
    print(f"{name} listening on http://127.0.0.1:{port} (ctrl+c to stop)")
    server.serve_forever()


def main() -> None:
    print(f"ANTHROPIC_MOCK_MODE={ANTHROPIC_MODE}  OPENAI_MOCK_MODE={OPENAI_MODE}")
    threads = [
        threading.Thread(target=_serve, args=(AnthropicHandler, ANTHROPIC_PORT, "anthropic-mock"), daemon=True),
        threading.Thread(target=_serve, args=(OpenAiHandler, OPENAI_PORT, "openai-mock"), daemon=True),
    ]
    for t in threads:
        t.start()
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\nstopping mocks")


if __name__ == "__main__":
    main()
