# Receiver native source provenance

The receiver derives from jqssun/android-airplay-server v0.0.33, commit e347ee51ff3cb164d6e758496a428e3ab9b1df02. Its C/C++ bridge, CMake configuration, patch files, Kotlin source and GPL license are included in airplay/. Exact upstream submodule revisions are recorded in airplay/native-sources.lock.json, fetched from that commit's Git tree.

For auditing or a full native rebuild, run `python scripts/fetch_native_sources.py`. This retrieves UxPlay, libplist, openssl-cmake and FFmpeg at those exact revisions. The upstream CMakeLists and patch files define the build; a native rebuild additionally requires the upstream NDK/CMake dependencies and application of the included UxPlay patches. Preserve the original license notices of each dependency.

The prepared APK build uses `scripts/prepare-native.py` to download the unchanged upstream 0.0.33 APK and verify its SHA-256 before extracting the protocol/audio runtime libraries. These binaries are generated build inputs, not source-controlled files. Their source is available through the pinned upstream project/dependencies above. This path does not extract programs from a user's TV or include device-specific data.
