"""Verify research copies, artifact inventories, and documentation links. No game execution."""
from pathlib import Path
import datetime, hashlib, json, re, zipfile
from urllib.parse import unquote

ROOT=Path(__file__).resolve().parents[1]
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
evidence=ROOT/'research/evidence'
inventory=json.loads((evidence/'binary-inventory.json').read_text(encoding='utf-8'))
checks=[]
for item in inventory:
    original=Path(item['source']); copy=ROOT/item['copy']
    checks.append(dict(source=str(original),copy=item['copy'],baseline=item['sha256'],source_matches=sha(original)==item['sha256'],copy_matches=sha(copy)==item['sha256']))
game=Path(r'C:\Program Files (x86)\Steam\steamapps\common\ProjectZomboid')
workshop=Path(r'C:\Program Files (x86)\Steam\steamapps\workshop\content\108600')
trees=[(workshop/'3807334881/mods/pz3d',ROOT/'reference/pz3d'),(workshop/'3619862853/mods/ZombieBuddy',ROOT/'reference/zombiebuddy/workshop')]
copy_checks=[]
for original_root,copy_root in trees:
    for original in original_root.rglob('*'):
        if original.is_file():
            copy=copy_root/original.relative_to(original_root)
            copy_checks.append(dict(source=str(original),copy=str(copy.relative_to(ROOT)),sha256=sha(original),matches=copy.is_file() and sha(original)==sha(copy)))
for copy in (ROOT/'reference/project-zomboid/config').iterdir():
    original=game/'jre64/release' if copy.name=='jre-release.txt' else game/copy.name
    copy_checks.append(dict(source=str(original),copy=str(copy.relative_to(ROOT)),sha256=sha(original),matches=sha(original)==sha(copy)))
original=game/'zbNative.dll'; copy=ROOT/'reference/zombiebuddy/binaries/zbNative.dll'
copy_checks.append(dict(source=str(original),copy=str(copy.relative_to(ROOT)),sha256=sha(original),matches=sha(original)==sha(copy)))
(evidence/'reference-file-verification.json').write_text(json.dumps(copy_checks,indent=2),encoding='utf-8')
artifacts=[]
for p in sorted((ROOT/'external').rglob('*')):
    if p.is_file() and 'sources' not in p.parts and p.suffix in ('.jar','.pom'):
        artifacts.append(dict(path=str(p.relative_to(ROOT)),bytes=p.stat().st_size,sha256=sha(p)))
(evidence/'external-artifacts.json').write_text(json.dumps(artifacts,indent=2),encoding='utf-8')
with zipfile.ZipFile(ROOT/'reference/project-zomboid/binaries/projectzomboid.jar') as gamejar, zipfile.ZipFile(ROOT/'external/openxr/lwjgl-openxr-3.4.1.jar') as xr:
    game_names=set(gamejar.namelist()); xr_names=set(xr.namelist())
    overlap=sorted(n for n in game_names & xr_names if n.endswith('.class') and not n.endswith('module-info.class'))
    xr_major=sorted({int.from_bytes(xr.read(n)[6:8],'big') for n in xr_names if n.startswith('org/lwjgl/openxr/') and n.endswith('.class')})
    binding=dict(game_openxr_classes=sum(n.startswith('org/lwjgl/openxr/') for n in game_names),overlapping_classes=overlap,openxr_base_class_major_versions=xr_major,game_wgl_present='org/lwjgl/opengl/WGL.class' in game_names,game_glfw_native_wgl_present='org/lwjgl/glfw/GLFWNativeWGL.class' in game_names)
(evidence/'openxr-static-compatibility.json').write_text(json.dumps(binding,indent=2),encoding='utf-8')
bad_links=[]
docs=[ROOT/'README.md',ROOT/'AGENTS.md',*(ROOT/'research').glob('*.md'),ROOT/'external/openxr/README.md']
for p in docs:
    if p.name=='original-request.md': continue
    for raw in re.findall(r'\]\(([^)]+)\)',p.read_text(encoding='utf-8')):
        if re.match(r'https?://',raw): continue
        target=unquote(raw.split('#')[0])
        if target and not (p.parent/target).exists(): bad_links.append(dict(file=str(p.relative_to(ROOT)),link=raw))
patches=json.loads((evidence/'patch-inventory.json').read_text(encoding='utf-8'))
result=dict(verified_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),binary_baseline_checks=checks,reference_files_checked=len(copy_checks),reference_mismatches=[x for x in copy_checks if not x['matches']],patches=len(patches),patches_without_target=[x['patch'] for x in patches if not x['candidate_descriptors']],broken_local_document_links=bad_links,openxr_static_compatibility=binding)
(evidence/'verification.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
assert all(x['source_matches'] and x['copy_matches'] for x in checks)
assert not result['reference_mismatches'] and not bad_links and not result['patches_without_target']
print(json.dumps({k:v for k,v in result.items() if k!='binary_baseline_checks'},indent=2))
