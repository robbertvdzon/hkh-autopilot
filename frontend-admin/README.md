# HKH admin frontend

Separate Flutter web administration application. Administrators sign in with the existing masked
Google token mechanism (no separate authorization-evidence input field) and can manage the latest
news, submit a single local collection record as an internal concept (`recordintake`), view the
external archieven.nl verification link for a record (`externalverification`) and inspect historical
search results through the authenticated `GET /api/admin/historical-search` route. The historical
view shows only safe provider identity fields and separate textual statuses/reasons for source
verification, metadata rights, privacy, public release and object/media rights. See
[../docs/factory/functional-spec.md](../docs/factory/functional-spec.md) and
[../docs/factory/technical-spec.md](../docs/factory/technical-spec.md) for the functional and
technical details.
