"""Read class-file metadata without loading or executing installed code."""
from pathlib import Path
import json, re, struct, zipfile
ROOT=Path(__file__).resolve().parents[1]

def parse(data):
    pos=0
    def take(n):
        nonlocal pos
        out=data[pos:pos+n]; pos+=n; return out
    def u1(): return take(1)[0]
    def u2(): return int.from_bytes(take(2),'big')
    def u4(): return int.from_bytes(take(4),'big')
    assert u4()==0xcafebabe
    minor,major=u2(),u2()
    cp=[None]*u2(); i=1
    while i<len(cp):
        tag=u1()
        if tag==1: cp[i]=take(u2()).decode('utf-8',errors='replace')
        elif tag in (3,4): take(4)
        elif tag in (5,6): take(8); i+=1
        elif tag in (7,8,16,19,20): cp[i]=u2()
        elif tag in (9,10,11,12,17,18): take(4)
        elif tag==15: take(3)
        else: raise ValueError(tag)
        i+=1
    access,this,superclass=u2(),u2(),u2()
    for _ in range(u2()): u2()
    def attributes():
        for _ in range(u2()): u2(); take(u4())
    def members():
        out=[]
        for _ in range(u2()):
            flags,name,desc=u2(),u2(),u2()
            out.append(dict(name=cp[name],descriptor=cp[desc],access_hex=hex(flags)))
            attributes()
        return out
    return dict(name=cp[cp[this]],major=major,access_hex=hex(access),fields=members(),methods=members())

paths={'pz3d':'reference/pz3d/42.20.4/media/java/client/PZ3D-0.2.2.jar','zombiebuddy':'reference/zombiebuddy/binaries/selected-research-classes.jar','vanilla':'reference/project-zomboid/binaries/projectzomboid.jar'}
patch_source=ROOT/'reference/pz3d/vineflower/com/pavelvoronin/pz3d/Patches.java'
source=patch_source.read_text(encoding='utf-8')
patches=[]
for m in re.finditer(r'@Patch\((.*?)\)\s+public static class (\w+)',source,re.S):
    a=m.group(1); c=re.search(r'className\s*=\s*"([^"]+)"',a); method=re.search(r'methodName\s*=\s*"([^"]+)"',a)
    if c and method: patches.append(dict(patch=m.group(2),target=c.group(1),method=method.group(1),line=source[:m.start()].count('\n')+1,annotation=a))
targets={p['target'].replace('.','/') for p in patches}
indexes={}
for key,path in paths.items():
    with zipfile.ZipFile(ROOT/path) as z:
        indexes[key]={}
        for n in z.namelist():
            if not n.endswith('.class'): continue
            if key=='vanilla' and not (n[:-6] in targets or n.startswith(('org/lwjgl/Version','org/lwjgl/opengl/WGL','org/lwjgl/glfw/GLFWNative','zombie/core/opengl/RenderThread','zombie/ui/UIManager','zombie/core/Core','zombie/GameWindow'))): continue
            d=parse(z.read(n)); indexes[key][d['name']]=d
    (ROOT/f'research/evidence/{key}-class-signatures.json').write_text(json.dumps(indexes[key],indent=2),encoding='utf-8')
rows=['# Installed PZ3D patch inventory','', 'Generated from installed-binary decompilation; descriptors verified directly against installed vanilla class files. Method-name-only patches may match several overloads; this table lists candidates, not a claim that every candidate is transformed. See annotation/signature inference in ZombieBuddy PatchEngine.','', '| Patch nested class | Target class.method | Installed candidate descriptors | Source line |','|---|---|---|---|']
for p in patches:
    cls=indexes['vanilla'].get(p['target'].replace('.','/'),{})
    sigs=[m['descriptor'] for m in cls.get('methods',[]) if m['name']==p['method']]
    p['candidate_descriptors']=sigs
    rows.append(f"| `{p['patch']}` | `{p['target']}.{p['method']}` | "+'<br>'.join('`'+s+'`' for s in sigs)+f" | [L{p['line']}](../reference/pz3d/vineflower/com/pavelvoronin/pz3d/Patches.java#L{p['line']}) |")
(ROOT/'research/patch-inventory.md').write_text('\n'.join(rows)+'\n',encoding='utf-8')
(ROOT/'research/evidence/patch-inventory.json').write_text(json.dumps(patches,indent=2),encoding='utf-8')
print(f'Indexed {len(patches)} patches; {sum(not p["candidate_descriptors"] for p in patches)} missing targets. Class versions: '+str({k:sorted({c['major'] for c in v.values()}) for k,v in indexes.items()}))
