"""Research-only extraction/inventory. Never imports or launches game classes."""
from pathlib import Path
import hashlib, json, re, zipfile

ROOT = Path(__file__).resolve().parents[1]
GAME = Path(r'C:\Program Files (x86)\Steam\steamapps\common\ProjectZomboid')
WORKSHOP = Path(r'C:\Program Files (x86)\Steam\steamapps\workshop\content\108600')
PAIRS = [(GAME/'projectzomboid.jar', ROOT/'reference/project-zomboid/binaries/projectzomboid.jar'),
         (GAME/'ZombieBuddy.jar', ROOT/'reference/zombiebuddy/binaries/ZombieBuddy.jar'),
         (WORKSHOP/'3807334881/mods/pz3d/42.20.4/media/java/client/PZ3D-0.2.2.jar', ROOT/'reference/pz3d/42.20.4/media/java/client/PZ3D-0.2.2.jar'),
         (WORKSHOP/'3619862853/mods/ZombieBuddy/libs/ZombieBuddy.jar', ROOT/'reference/zombiebuddy/workshop/libs/ZombieBuddy.jar')]
def digest(p): return hashlib.sha256(p.read_bytes()).hexdigest()
inventory=[]
for source, copy in PAIRS:
    inventory.append(dict(source=str(source),copy=str(copy.relative_to(ROOT)),bytes=copy.stat().st_size,sha256=digest(copy),source_sha256=digest(source)))
    with zipfile.ZipFile(copy) as z:
        prefix='workshop-' if 'workshop' in str(copy) else ''
        (ROOT/'research/evidence'/f'{prefix}{copy.stem}-entries.txt').write_text('\n'.join(z.namelist()),encoding='utf-8')
        mf=next((n for n in z.namelist() if n.upper()=='META-INF/MANIFEST.MF'),None)
        if mf:
            text=z.read(mf).decode(errors='replace').replace('\r\n','\n').replace('\r','')
            (ROOT/'research/evidence'/f'{prefix}{copy.stem}-manifest-header.txt').write_text(text.split('\n\n')[0],encoding='utf-8')
(ROOT/'research/evidence/binary-inventory.json').write_text(json.dumps(inventory,indent=2),encoding='utf-8')
log=(Path.home() / 'Zomboid' / 'console.txt').read_text(encoding='utf-8',errors='replace').splitlines()
patterns=r'java.runtime.version=|java.class.version=|version=42|OpenGL version|Graphics card|GPU|LWJGL|Java prototype|Release build=|Output .*framebuffer|ZombieBuddy.getVersion|Excluded: .*agent|added to classpath: .*PZ3D'
(ROOT/'research/evidence/existing-console-excerpts.txt').write_text('\n'.join(f'{i+1}: {s}' for i,s in enumerate(log) if re.search(patterns,s)),encoding='utf-8')
with zipfile.ZipFile(PAIRS[0][1]) as z:
    names=['zombie/GameWindow','zombie/gameStates/IngameState','zombie/core/Core','zombie/core/SpriteRenderer','zombie/core/sprite/SpriteRenderState','zombie/core/sprite/SpriteRenderStateData','zombie/core/opengl/RenderThread','zombie/ui/UIManager','zombie/core/textures/TextureFBO','zombie/core/textures/TextureDraw','zombie/iso/IsoCamera','zombie/iso/PlayerCamera','zombie/iso/IsoWorld','zombie/iso/IsoCell','zombie/core/skinnedmodel/model/ModelInstanceRenderData','zombie/core/skinnedmodel/model/ModelInstanceTextureInitializer','org/lwjgl/Version','org/lwjglx/opengl/Display']
    out=ROOT/'reference/project-zomboid/selected-classes'
    for n in z.namelist():
        if n.endswith('/'): continue
        if any(n==c+'.class' or n.startswith(c+'$') for c in names) or n.endswith('pom.properties') or (n.startswith('META-INF/') and 'lwjgl' in n.lower()):
            target=out/n; target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(z.read(n))
    selected=ROOT/'reference/project-zomboid/binaries/selected-research-classes.jar'
    with zipfile.ZipFile(selected,'w') as target:
        for p in out.rglob('*.class'): target.write(p,p.relative_to(out).as_posix())
    additional=['zombie/core/skinnedmodel/advancedanimation/AnimatedModel','zombie/core/sprite/SpriteRendererStates','zombie/core/sprite/SpriteRenderState','zombie/iso/fboRenderChunk/FBORenderCell','zombie/ui/UITransition']
    with zipfile.ZipFile(ROOT/'reference/project-zomboid/binaries/additional-research-classes.jar','w') as target:
        for n in z.namelist():
            if any(n==p+'.class' or n.startswith(p+'$') for p in additional): target.writestr(n,z.read(n))
with zipfile.ZipFile(PAIRS[2][1]) as z:
    for n in z.namelist():
        if not n.endswith('.class') and not n.endswith('/'):
            p=ROOT/'reference/pz3d/jar-resources'/n; p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(z.read(n))
with zipfile.ZipFile(PAIRS[1][1]) as z, zipfile.ZipFile(ROOT/'reference/zombiebuddy/binaries/selected-research-classes.jar','w') as target:
    for n in z.namelist():
        if n.startswith('me/zed_0xff/zombie_buddy/') and n.endswith('.class'): target.writestr(n,z.read(n))
print('Reference inventories, excerpts, resources and selected class archives saved.')
