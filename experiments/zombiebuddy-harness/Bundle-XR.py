"""Embed only the pinned OpenXR bindings/native loader; retain the game's LWJGL core."""
from pathlib import Path
import hashlib, json, sys, zipfile
root=Path(__file__).resolve().parents[2]
diagnostic=root/'experiments/openxr-diagnostic'
items=[i for i in json.loads((diagnostic/'dependencies.json').read_text())['libraries'] if i['file'].startswith('lwjgl-openxr-')]
assert len(items)==2
with zipfile.ZipFile(sys.argv[1],'a',compression=zipfile.ZIP_DEFLATED) as output:
    existing=set(output.namelist())
    for item in items:
        path=diagnostic/'lib'/item['file']
        assert hashlib.sha256(path.read_bytes()).hexdigest()==item['sha256'], f'Altered dependency: {path}'
        with zipfile.ZipFile(path) as source:
            for entry in source.infolist():
                name=entry.filename
                if entry.is_dir() or name.endswith('module-info.class') or name=='META-INF/MANIFEST.MF': continue
                if name in existing: continue
                output.writestr(name,source.read(entry)); existing.add(name)
print('Bundled pinned LWJGL OpenXR bindings and Windows native loader.')
