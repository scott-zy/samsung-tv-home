"""Remove nonvisual PNG text/EXIF and JPEG EXIF/comments; compressed image pixels stay unchanged."""
from pathlib import Path
import struct
root=Path(__file__).resolve().parents[1]
changed=0
for path in (root/'launcher/src/main/assets/home').iterdir():
    data=path.read_bytes();out=data
    if data.startswith(b'\x89PNG\r\n\x1a\n'):
        blocks=[data[:8]];at=8
        while at<len(data):
            size=struct.unpack('>I',data[at:at+4])[0];end=at+size+12;kind=data[at+4:at+8]
            if kind not in {b'tEXt',b'zTXt',b'iTXt',b'eXIf',b'tIME'}:blocks.append(data[at:end])
            at=end
        out=b''.join(blocks)
    elif data.startswith(b'\xff\xd8'):
        blocks=[data[:2]];at=2
        while at<len(data):
            if data[at]!=255:raise ValueError('Unexpected JPEG structure')
            marker=data[at+1]
            if marker in (0xDA,0xD9):blocks.append(data[at:]);break
            length=int.from_bytes(data[at+2:at+4],'big');end=at+2+length
            if marker not in (0xE1,0xFE):blocks.append(data[at:end])
            at=end
        out=b''.join(blocks)
    if out!=data:path.write_bytes(out);changed+=1
print(f'Removed nonvisual metadata from {changed} assets')
