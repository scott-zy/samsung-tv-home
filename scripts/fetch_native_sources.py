"""Optional: retrieve exact native dependency sources for auditing/full CMake builds."""
from pathlib import Path
import json,subprocess
root=Path(__file__).resolve().parents[1]
for item in json.loads((root/'airplay/native-sources.lock.json').read_text())['dependencies']:
    target=root/'airplay/src/main/cpp/third_party'/item['name']
    if (target/'.git').exists():
        current=subprocess.check_output(['git','rev-parse','HEAD'],cwd=target,text=True).strip()
        if current!=item['commit']:raise SystemExit('Existing native checkout differs: '+item['name'])
        continue
    if target.exists() and any(target.iterdir()):raise SystemExit('Nonempty source directory: '+item['name'])
    subprocess.run(['git','clone','--filter=blob:none','--no-checkout',item['repository'],str(target)],check=True)
    subprocess.run(['git','checkout','--detach',item['commit']],cwd=target,check=True)
print('Native dependency sources match pinned upstream revisions')
