from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
from privacy_check import ROOT,audit
files,issues=audit()
if issues:raise SystemExit('\n'.join(sorted(set(issues))))
output=ROOT/'artifacts/samsung-tv-home-source.zip';output.parent.mkdir(exist_ok=True)
with ZipFile(output,'w',ZIP_DEFLATED) as archive:
    for path in files:archive.write(path,path.relative_to(ROOT).as_posix())
print(f'Created source-only archive: {len(files)} files (no Git history, keys, evidence, configuration or build outputs)')
