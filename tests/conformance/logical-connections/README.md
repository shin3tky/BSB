# logical connections conformance data

This directory fixes independent expectations for logical connections before production implementation.
`catalog.tsv` owns the exact N15/F15/R15 ID set. The remaining tables use explicit keys and are checked by
`tools/logical-connections_data.py --check` and `ConnectionConformanceDataTest`. `manifest.tsv` covers every physical asset
except this README and the manifest itself.

The fake resolver never performs DNS, TLS, or network I/O. Marker values in `policies.tsv` exist only to prove
that policies, credential references, secrets, and host failures do not reach public outputs.
