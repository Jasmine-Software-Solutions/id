# Cross-device sign-in handoff

A signed-in browser can hand its Jasmine ID session to a second device (phone,
tablet, another laptop) without signing the first device out. The first browser
embeds a page that shows a QR code; the second device scans it, the first
browser confirms, and the second device receives a normal authorization code.

The original session stays active. The second device gets a **new** session and
a delegated session, exactly as if it had signed in on its own.

## Embedding

Embed this URL in a **same-site** iframe (`*.jasmine.software` →
`id.jasmine.software`):

```
https://id.jasmine.software/embed/#/handoff?client_id=<uuid>&redirect_uri=<urlencoded>&state=<opaque>
```

`/embed` is the frameable namespace; the hash names the embedded page
(`/handoff` today) and carries its parameters. The hash is never sent to the
server, so the edge sees only `/embed` — that is what the frame policy is
scoped to. Add a future embeddable page by registering it in the embed router
and pointing clients at `/embed/#/<page>?…`; do not add a new top-level path.

Optional parameters:

| Parameter | Meaning |
|---|---|
| `scope` | Space-separated OAuth2 scopes for the handoff code. When omitted, the code grants `openid profile email`, so an ID token is issued. Include `openid` in a custom list if an ID token is needed. |
| `nonce` | OIDC nonce for the ID token. |

The pathname must be **`/embed`** (with or without the trailing slash): the
frame permission is scoped to that path, and a reload inside the iframe
re-requests it. Omitting the hash does not quietly pick a page — `/embed/` with
no route renders the invalid-embed page.

`/embed` is the only frameable path. Everything else — including `/handoff`,
which remains a top-level link form — is sent `frame-ancestors 'self'` and
cannot be embedded. Only embed UI is routable under `/embed`; any other hash
route renders an invalid-embed page.

The scanning device does not use `/embed`: it opens the QR code's URL at
`https://id.jasmine.software/#/handoff/scan?…` top-level, where it is not
framed.

Do not sandbox the iframe without `allow-same-origin allow-scripts
allow-popups`. Give it a fixed size of roughly **360 × 480 px**; the card keeps
the same width and height in every state, so it never resizes the embed.

The first instance to load creates the handoff. Later instances for the same ID
session attach to it, even if their `client_id`/`redirect_uri`/`state`/`scope`/
`nonce` differ — those are validated for the requesting page but do **not**
change the handoff. The first instance's parameters are authoritative; use the
same values across duplicate embeds.

Two **different** clients cannot have simultaneous active handoffs for one ID
session: the second attaches to the first's handoff, and the code is issued to
the first client. If per-client isolation is ever required, the uniqueness rule
would need to be scoped to `(session, client)`.

## States and interaction

| Card state | What it means | What the user does |
|---|---|---|
| QR code | Waiting for a scan. The code rotates automatically about every two minutes; the embed can stay open for hours. | Scan with the second device. |
| Lock + **Open on your phone** / **Show QR** | The frame cannot see ID cookies (not signed in, or a cross-site parent). | Click **Show QR** and sign in through the new window; the card then shows the QR code. |
| Warning + **No** / **Yes** | A device scanned the code. Every instance shows this. | **Yes** if it was them, **No** otherwise. Either button works from any instance. |
| Green check + **Change devices** | The second device was signed in. | Optionally drop it again and start over. |

A scan in one instance is visible in all instances of the same handoff. **No**
returns every instance to a fresh QR code. **Yes** lets the scanning device
claim its new session; every instance then shows the confirmation state.

The handoff ends when the original ID session ends (expiry or revocation); the
card then asks the user to sign in again.

If a new handoff is created — a new embed load after an earlier one was
confirmed/claimed — the old instances show "This handoff was replaced in
another tab." The earlier +1 device stays signed in until the **new** handoff is
confirmed, at which point ID revokes it.

### Cross-site parents

A parent on a different site origin cannot see the ID cookies, so the iframe
shows the **Open on your phone** state and its **Show QR** button opens the
handoff URL in a new window, where the page runs top-level and cookies are
first-party; after signing in, that window shows the QR code. The parent does
not need to do anything for this.

## The callback

The second device is redirected to the registered `redirect_uri` with:

```
redirect_uri?code=…&state=…&handoff=true
```

`state` is the value the first instance supplied (omitted when it supplied
none). `handoff=true` distinguishes the callback from an ordinary sign-in.

**The `oauth2_state` cookie does not exist on the new browser.** The new device
never visited the client before the callback, so the usual cookie comparison
cannot work. Validate the handoff state by another means — a signed/encoded
state value, or a server-side record created before the handoff — and do not
skip validation entirely.

## Exchanging the code

Exchange the code exactly as today: `POST /api/v1/oauth2/token` with
`grant_type=authorization_code`, `client_id`, `redirect_uri`, `code`, and the
client's Basic credentials.

ID re-checks the client's `AcceptHandoff` permission on the original session
resource and enforces that the exchanging client is the client that created the
handoff. The code is short-lived (10 minutes) like any authorization code.

## Permissions

Handoff is opt-in per client. The client needs an `AcceptHandoff` policy on the
sessions it may hand off, assigned to the client principal:

```json
[{
  "effect": "ALLOW",
  "actions": ["id.jasmine.software:AcceptHandoff"],
  "resources": ["id.jasmine.software://account:*/session:*"]
}]
```

The permission is checked when the embed loads, when the user confirms, and
again when the code is exchanged. Removing it mid-flow fails the confirmation
or the exchange.

## Sessions after the handoff

- **Change devices** deletes the new session and its delegated session. The
  client's next refresh fails. Already-issued access tokens (five minutes) and
  ID/refresh JWTs cannot be revoked; ID has no token revocation endpoint.
- Confirming a **new** handoff after an earlier +1 session exists revokes the
  earlier +1 session at that confirmation. The earlier session stays active
  until then, and its refresh starts failing afterwards.

## Minimal client checklist

1. Add the client to an `AcceptHandoff` policy.
2. Embed `/embed/#/handoff?client_id=…&redirect_uri=…&state=…` in a fixed-size
   iframe with `allow-same-origin allow-scripts allow-popups`.
3. Treat `handoff=true` callbacks as a distinct state-validation case; never
   compare to `oauth2_state`.
4. Exchange the code as usual, handling a 403 as "permission removed".
5. Expect the +1 device's refresh to start failing after Change devices or a
   newer handoff confirmation.
