# Release signing

No signing identity is committed. Debug builds use a disposable development identity. Release builds remain unsigned when the signing environment is absent; there is no fallback to a personal or debug release key.

For an independently generated release keystore stored outside this repository, supply these environment variables only to the build process:

- RELEASE_STORE_FILE: absolute path to the keystore outside the source tree
- RELEASE_STORE_PASSWORD: keystore password
- RELEASE_KEY_ALIAS: alias, default release
- RELEASE_KEY_PASSWORD: key password, optional if identical to the store password

Run `gradlew :launcher:testDebugUnitTest :launcher:assembleRelease :airplay:assembleRelease`. Keep backups of the release identity separately; future APK updates need the same identity. Never include a keystore, password file, personal signing.properties or CI secret value in source/release archives.

The public application IDs are org.s95dhome.launcher and org.s95dhome.airplay, independent from the local development versions. Install them as separate applications for clean-install testing; they do not inherit a developer device's addresses, certificate approvals or saved application list.
