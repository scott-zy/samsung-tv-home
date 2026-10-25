"""Fetch the unmodified upstream receiver APK by content hash; extract runtime libraries into ignored build output."""
from pathlib import Path
import urllib.request,hashlib,zipfile,io
root=Path(__file__).resolve().parents[1]
url='https://f-droid.org/repo/io.github.jqssun.airplay_33.apk'
expected='2203dcc76ad884265e6e01c2147c917f100e93f37b91bf728bb3eed466396b32'
cache=root/'airplay/build/upstream/airplay.apk';cache.parent.mkdir(parents=True,exist_ok=True)
if not cache.exists():
    with urllib.request.urlopen(url,timeout=90) as response:cache.write_bytes(response.read(64*1024*1024+1))
data=cache.read_bytes()
if hashlib.sha256(data).hexdigest()!=expected:raise SystemExit('Upstream APK checksum mismatch; no files extracted')
output=root/'airplay/build/generated/jniLibs'
with zipfile.ZipFile(io.BytesIO(data)) as archive:
    for abi in ('armeabi-v7a','arm64-v8a','x86_64'):
        for name in ('libairplay_native.so','libcrypto.so','liboboe.so','libc++_shared.so'):
            target=output/abi/name;target.parent.mkdir(parents=True,exist_ok=True)
            target.write_bytes(archive.read('lib/'+abi+'/'+name))
print('Verified upstream APK; prepared 12 runtime libraries in ignored build directory')
