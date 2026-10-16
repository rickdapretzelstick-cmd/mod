# The converter as a stand-alone folder for running on your own PC: the scripts the pipeline uses, the avatar's
# palette, the part network, the rig's clips (for the viewer), launchers and a README (tools/pc_package/).
#   python3 tools/make_pc_package.py [OUT.zip]      (default build/jjk_converter.zip)
import sys, os, shutil, zipfile

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
SCRIPTS = ['r6_batch.py', 'r6_convert.py', 'r6_takes.py', 'r6_track_rep.py', 'r6_global.py', 'r6_partfit.py',
           'r6_offsets.py', 'r6_align.py', 'r6_limbpass.py', 'r6_neutral.py', 'r6_oversee.py', 'r6_game_clip.py',
           'r6_fit.py', 'r6_fit_torch.py', 'r6_fit_selftest.py', 'r6_glyph.py', 'r6_partnet.py', 'r6_partscore.py',
           'r6_synth.py', 'r6_rects.py', 'r6_motion.py', 'r6_fuse_views.py', 'build_anim_viewer.py']
DIRS = [('tools/palettes', lambda f: f.endswith('.json') and '.tmp.' not in f), ('tools/anim_viewer', None),
        ('src/main/resources/assets/jjk/animations', None)]
FILES = ['build/synth/partnet2.pt']
REQUIREMENTS = 'numpy>=2.0\nscipy>=1.13\nopencv-python>=4.10\ntorch>=2.5\npillow>=10.0\n'


def main():
    out = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'build', 'jjk_converter.zip'))
    top = 'jjk_converter'
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
        for s in SCRIPTS:
            z.write(os.path.join(TOOLS, s), f'{top}/tools/{s}')
        for d, keep in DIRS:
            for base, _, files in os.walk(os.path.join(ROOT, d)):
                if '__pycache__' in base:
                    continue
                for f in files:
                    if keep is None or keep(f):
                        p = os.path.join(base, f)
                        z.write(p, f'{top}/{os.path.relpath(p, ROOT)}')
        for f in FILES:
            z.write(os.path.join(ROOT, f), f'{top}/{f}')
        pkg = os.path.join(TOOLS, 'pc_package')
        for f in os.listdir(pkg):
            info = zipfile.ZipInfo(f'{top}/{f}')
            info.external_attr = (0o755 if f.endswith('.sh') else 0o644) << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, open(os.path.join(pkg, f), 'rb').read())
        z.writestr(f'{top}/requirements.txt', REQUIREMENTS)
    print(out, f'{os.path.getsize(out) / 1e6:.1f} MB')


if __name__ == '__main__':
    main()
