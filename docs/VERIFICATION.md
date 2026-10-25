# Preparation verification

This is an experimental source release, not a claim of compatibility with every Samsung television. The generalized first-run flow still needs a clean-install test on real hardware before a stable APK release.

Completed checks:

- Both release APKs compile; Android lint checks pass. The launcher's specific target-SDK exemption and firmware constraints are documented in PRIVACY.md.
- Four Java regression groups pass, covering application order, Home-event races, source parsing, local-address validation, diagnostic redaction and the TLS source protocol. A wrong certificate fails before business requests are sent.
- Three publication-gate tests pass, including exclusion of signing/configuration/evidence files and rejection of fixed private endpoint defaults.
- Browser checks cover application management, ordering, focus and generic HDMI cards.
- Release applications use separate public application IDs. Both APKs use the same new release certificate, independent of the earlier local development identity. No private key is included.

Public release certificate SHA-256:

`0BBEA0D28A47FC1E48AA4E94F63F0F889EDF7674094ADFE304E0402C7126D712`

Before a stable APK release, test these flows on the television:

1. Disable Home-return in any previous launcher and stop other AirPlay receivers. Install the public builds alongside them.
2. Complete first-use TV detection or manual address entry, review the certificate and save trust. Confirm restart retains the approved connection.
3. Check HDMI status and switching, Home-return from apps and HDMI, and application add/hide/order persistence.
4. Check local diagnostic export or its copy-text fallback, and AirPlay discovery as TV Airplay.

Run `python -m unittest discover -s tests -v`, `python scripts/privacy_check.py` and `python scripts/build_source_archive.py` when preparing the source release. Generated artifacts, build reports, machine settings and signing credentials remain outside the publication source set.
