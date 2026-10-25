from pathlib import Path
import importlib.util,tempfile,unittest
ROOT=Path(__file__).resolve().parents[1]
FIXTURES=(ROOT/'build/publication-fixtures').resolve()
assert FIXTURES.is_relative_to(ROOT.resolve())
FIXTURES.mkdir(parents=True,exist_ok=True)
spec=importlib.util.spec_from_file_location('privacy_check',ROOT/'scripts/privacy_check.py')
gate=importlib.util.module_from_spec(spec);spec.loader.exec_module(gate)
class PublicationGateTest(unittest.TestCase):
    def test_whitelist_omits_local_material(self):
        with tempfile.TemporaryDirectory(dir=FIXTURES) as d:
            root=Path(d)
            for name in ['README.md','launcher/src/main/Example.java','.sign/release.p12','device-evidence/report.json','local.properties','artifacts/result.apk','launcher/build/log.txt','.env']:
                path=root/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text('fixture')
            self.assertEqual({p.relative_to(root).as_posix() for p in gate.source_files(root)},{'README.md','launcher/src/main/Example.java'})
    def test_private_endpoint_fails_gate(self):
        with tempfile.TemporaryDirectory(dir=FIXTURES) as d:
            root=Path(d);p=root/'launcher/Example.java';p.parent.mkdir();p.write_text('endpoint="10.23.45.67"')
            self.assertTrue(any('private endpoint' in issue for issue in gate.audit(root)[1]))
    def test_documentation_endpoint_is_not_device_binding(self):
        with tempfile.TemporaryDirectory(dir=FIXTURES) as d:
            root=Path(d);(root/'README.md').write_text('Documentation address: 192.0.2.10')
            self.assertEqual(gate.audit(root)[1],[])
if __name__=='__main__':unittest.main()
