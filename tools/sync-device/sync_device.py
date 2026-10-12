"""Máy thứ hai giả lập cho kiểm tra sync thật (LEDGER-PLAN Phase 6).

Đăng nhập Keycloak bằng Authorization Code + PKCE như app (không cần bật direct grant), rồi gọi
/api/v1/sync/push và /api/v1/sync/pull bằng token thật. Chỉ dùng stdlib.

    export FINANCE_BASE=https://xxx.trycloudflare.com   # URL Keycloak/API mà app đang dùng (issuer của token)
    export FINANCE_API=http://localhost:8080             # tuỳ chọn: gọi API thẳng, khỏi đi vòng qua tunnel
    python3 -I sync_device.py login B user@example.test 'mật khẩu'
    python3 -I sync_device.py pull B                      # in thay đổi từ cursor đã lưu, rồi lưu cursor mới
    python3 -I sync_device.py push B < ops.json           # ops.json = mảng SyncOperation theo contract

Trạng thái mỗi máy (token, deviceId, cursor) nằm ở ./state/<tên>.json — đã gitignore. Access token sống 5 phút:
hết hạn thì chạy lại `login`, deviceId và cursor được giữ nguyên.
"""
import base64
import hashlib
import html
import html.parser
import http.cookiejar
import json
import os
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

BASE = os.environ.get("FINANCE_BASE")
API = os.environ.get("FINANCE_API") or BASE
CLIENT_ID = "finance-mobile"
REDIRECT = "com.uit.finance://oauth/callback"
TIMEOUT = 30
STATE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "state")


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class _FormAction(html.parser.HTMLParser):
    def __init__(self):
        super().__init__()
        self.action = None

    def handle_starttag(self, tag, attrs):
        if tag == "form" and self.action is None:
            self.action = dict(attrs).get("action")


def _b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def login(email: str, password: str) -> str:
    realm = f"{BASE}/realms/finance/protocol/openid-connect"
    jar = http.cookiejar.CookieJar()
    browser = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    no_redirect = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar), _NoRedirect)
    verifier = _b64url(secrets.token_bytes(32))
    query = urllib.parse.urlencode({
        "client_id": CLIENT_ID, "response_type": "code", "redirect_uri": REDIRECT, "scope": "openid",
        "code_challenge": _b64url(hashlib.sha256(verifier.encode()).digest()),
        "code_challenge_method": "S256", "state": secrets.token_hex(8),
    })
    form = _FormAction()
    form.feed(browser.open(f"{realm}/auth?{query}", timeout=TIMEOUT).read().decode())
    credentials = urllib.parse.urlencode({"username": email, "password": password}).encode()
    try:
        no_redirect.open(html.unescape(form.action), credentials, timeout=TIMEOUT)
        raise SystemExit("Keycloak không redirect về app: sai email/mật khẩu hoặc tài khoản chưa verify")
    except urllib.error.HTTPError as redirect:
        location = redirect.headers["Location"]
    code = urllib.parse.parse_qs(urllib.parse.urlparse(location).query)["code"][0]
    exchange = urllib.parse.urlencode({
        "grant_type": "authorization_code", "client_id": CLIENT_ID, "code": code,
        "redirect_uri": REDIRECT, "code_verifier": verifier,
    }).encode()
    return json.load(browser.open(f"{realm}/token", exchange, timeout=TIMEOUT))["access_token"]


def _path(name):
    return os.path.join(STATE_DIR, f"{name}.json")


def load(name):
    with open(_path(name)) as f:
        return json.load(f)


def save(name, state):
    os.makedirs(STATE_DIR, exist_ok=True)
    with open(_path(name), "w") as f:
        json.dump(state, f, indent=1)


def call(state, method, path, body=None):
    headers = {"Authorization": f"Bearer {state['token']}", "Content-Type": "application/json"}
    if method == "POST":
        headers["Idempotency-Key"] = str(uuid.uuid4())
    request = urllib.request.Request(f"{API}{path}", method=method, headers=headers,
                                     data=None if body is None else json.dumps(body).encode())
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read() or b"{}")


def pull_all(state):
    changes = []
    while True:
        since = "" if state.get("cursor") is None else f"&since={state['cursor']}"
        status, page = call(state, "GET", f"/api/v1/sync/pull?limit=500{since}")
        if status != 200:
            raise SystemExit(f"pull {status}: {page}")
        changes += page["changes"]
        state["cursor"] = page["nextCursor"]
        if not page["hasMore"]:
            return changes


def main():
    if not BASE:
        raise SystemExit("Thiếu FINANCE_BASE (URL mà app dùng cho Keycloak, vd https://xxx.trycloudflare.com)")
    command, name, *rest = sys.argv[1:]
    if command == "login":
        try:
            state = load(name)
        except FileNotFoundError:
            state = {"device": str(uuid.uuid4()), "cursor": None}
        state["token"] = login(*rest)
        save(name, state)
        print("ok")
    elif command == "pull":
        state = load(name)
        print(json.dumps(pull_all(state), ensure_ascii=False))
        save(name, state)
    elif command == "push":
        state = load(name)
        body = {"deviceId": state["device"], "ops": json.load(sys.stdin)}
        status, result = call(state, "POST", "/api/v1/sync/push", body)
        print(status, json.dumps(result, ensure_ascii=False))
    else:
        raise SystemExit(f"Lệnh lạ: {command} (login | pull | push)")


if __name__ == "__main__":
    main()
