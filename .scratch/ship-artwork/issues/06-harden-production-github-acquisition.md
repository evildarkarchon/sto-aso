Status: ready-for-agent
Blocked by: 03

# 06: Harden production GitHub acquisition

## What to build

Connect Ship Artwork to its fixed GitHub source through a private production adapter that rejects untrusted, malformed, or excessive responses as nonfatal artwork failures.

## Acceptance criteria

- [ ] Production acquisition uses HTTPS with a five-second connection timeout and a fifteen-second request timeout.
- [ ] Redirects are accepted only between the approved GitHub HTTPS origins.
- [ ] Responses larger than two MiB, unexpected content types, incomplete decodes, and invalid image data are rejected.
- [ ] Decoded dimensions larger than 2048 by 2048 or otherwise invalid dimensions are rejected before composition.
- [ ] Every rejection becomes the same nonfatal acquisition outcome consumed by Ship Artwork retry and fallback behavior.
- [ ] Tests script success and every rejection category without accessing the real network.
- [ ] The transport remains private so application callers cannot choose an origin or provider.
