Status: resolved
Blocked by: 03

# 06: Harden production GitHub acquisition

## What to build

Connect Ship Artwork to its fixed GitHub source through a private production adapter that rejects untrusted, malformed, or excessive responses as nonfatal artwork failures.

## Acceptance criteria

- [x] Production acquisition uses HTTPS with a five-second connection timeout and a fifteen-second request timeout.
- [x] Redirects are accepted only between the approved GitHub HTTPS origins.
- [x] Responses larger than two MiB, unexpected content types, incomplete decodes, and invalid image data are rejected.
- [x] Decoded dimensions larger than 2048 by 2048 or otherwise invalid dimensions are rejected before composition.
- [x] Every rejection becomes the same nonfatal acquisition outcome consumed by Ship Artwork retry and fallback behavior.
- [x] Tests script success and every rejection category without accessing the real network.
- [x] The transport remains private so application callers cannot choose an origin or provider.

## Comments

Connected application opening to the internal fixed GitHub PNG adapter. Requests
use a five-second connection timeout and fifteen-second request/body deadline;
redirects are followed manually only across github.com and
raw.githubusercontent.com HTTPS origins, with loop and hop limits. Closing the
application artwork lifetime aborts its HTTP client without waiting.

Body subscribers enforce the inclusive two-MiB ceiling while streaming. Header,
length, PNG framing/CRC, complete zlib stream/checksum, and dimension checks reject
malformed or excessive data before composition. All failures enter the existing
nonfatal fallback/backoff path. Internal offline and scripted construction remain
available without exposing transport configuration to application callers.

Scripted HTTP tests cover success, approved and rejected redirects, both dimension
boundaries, inclusive byte limits and streaming cancellation, bad content/encoding,
invalid lengths/data/checksums, incomplete container and compressed streams, and
connection/request timeout, cancellation, and I/O failure outcomes. They assert
fallback retention, backoff, and successful explicit retry through Ship Artwork.
Timeout exceptions are scripted; timer expiry and a real online refresh were not
performed. Existing offline pixel baselines continue to use offline construction.

Standards review found no violations; its duplicated-policy suggestion was
addressed with named limits. Spec review identified ImageIO accepting incomplete
zlib endings; a failing through-seam regression and explicit bounded inflation
fixed it, and re-review found no remaining blocking findings. Updated previously
stage-specific comments/Javadoc for production opening, resources, and shutdown.

Verification: focused Ship Artwork tests passed, followed by
`.\gradlew.bat clean build`, including the complete JUnit suite and exploded,
packaged, and thin-JAR verification. No data-format, XML, layout, or public
application interface changes.
