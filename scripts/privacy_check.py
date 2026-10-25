"""Audit the source-only publication set, independently of .gitignore."""
from pathlib import Path
import re,subprocess,sys
ROOT=Path(__file__).resolve().parents[1]
SKIP_DIRS={'.git','.gradle','.kotlin','.tools','build','artifacts','device-evidence','logs','.sign','.idea','.vscode','__pycache__'}
SKIP_NAMES={'local.properties','signing.properties','.env','tv-connection.json'}
SECRET_SUFFIXES={'.jks','.keystore','.p12','.pfx','.pem','.key','.cer','.crt','.password','.db','.sqlite','.sqlite3','.apk','.aab','.apks','.zip','.log','.dump'}
def excluded(path):
    return bool(set(path.parts)&SKIP_DIRS) or path.name in SKIP_NAMES or path.name.startswith('.env.') or path.suffix.lower() in SECRET_SUFFIXES or path.name.endswith('.local.json')
def source_files(root=ROOT):
    for path in sorted(root.rglob('*')):
        rel=path.relative_to(root)
        if excluded(rel):continue
        if path.is_symlink():raise ValueError('Symlink in publication set: '+str(rel))
        if path.is_file():yield path
def audit(root=ROOT):
    issues=[];files=list(source_files(root))
    if (root/'.git').exists():
        tracked=subprocess.check_output(['git','ls-files','-z'],cwd=root).decode().split('\0')
        issues += ['forbidden tracked file: '+name for name in tracked if name and excluded(Path(name))]
    for path in files:
        rel=path.relative_to(root);data=path.read_bytes()
        if path.suffix.lower() in {'.so','.dex','.class','.exe'}:issues.append('binary build input: '+str(rel));continue
        if path.suffix.lower() in {'.png','.jpg','.jpeg','.woff2','.jar'}:
            if path.suffix=='.jar' and str(rel).replace('\\','/')!='gradle/wrapper/gradle-wrapper.jar':issues.append('unexpected binary archive: '+str(rel))
            continue
        try:text=data.decode('utf-8')
        except UnicodeDecodeError:issues.append('unknown binary: '+str(rel));continue
        # Concrete addresses in fixtures are documentation/loopback examples; no personal endpoint defaults.
        if 'src/test/' not in str(rel).replace('\\','/') and 'tests' not in rel.parts:
            for address in re.findall(r'\b(?:\d{1,3}\.){3}\d{1,3}\b',text):
                n=address.split('.')
                if n[0]=='10' or n[:2]==['192','168'] or (n[0]=='172' and 16<=int(n[1])<=31):issues.append('private endpoint literal: '+str(rel))
        if re.search(r'(?i)[A-Z]:[/\\](?:Users|S95D-Home)[/\\]',text):issues.append('machine-specific path: '+str(rel))
        if re.search(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',text):issues.append('private key material: '+str(rel))
    return files,issues
if __name__=='__main__':
    files,issues=audit()
    if issues:
        print('\n'.join(sorted(set(issues))));sys.exit(1)
    print(f'PASS: {len(files)} source files; no tracked secret/build inputs, private endpoint defaults or machine-specific paths')
