# Builds the converter app (JJKConverter.exe on Windows) into build/app/JJKConverter and zips it to
# build/JJKConverter-<os>.zip: PyInstaller (jjk.spec) for the programs, then the scripts and data next to them.
#   python tools/exe/build_exe.py [--no-zip]
# Needs: pip install pyinstaller numpy scipy opencv-python torch pillow  (on Windows, from the python.org installer,
# which includes tkinter). Run it on the system the app is for: PyInstaller does not cross-build.
import sys, os, shutil, subprocess, zipfile, platform

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
ROOT = os.path.dirname(TOOLS)
sys.path.insert(0, TOOLS)
import make_pc_package as MP


def main():
    work, dist = os.path.join(ROOT, 'build', 'pyinstaller'), os.path.join(ROOT, 'build', 'app')
    subprocess.run([sys.executable, '-m', 'PyInstaller', '--noconfirm', '--clean', '--workpath', work, '--distpath',
                    dist, os.path.join(HERE, 'jjk.spec')], check=True)
    app = os.path.join(dist, 'JJKConverter')
    for src, rel in MP.entries():
        dst = os.path.join(app, *rel.split('/'))
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy2(src, dst)
    shutil.copy2(os.path.join(HERE, 'README.txt'), os.path.join(app, 'README.txt'))
    print(app)
    if '--no-zip' in sys.argv:
        return
    name = {'Windows': 'windows', 'Darwin': 'mac'}.get(platform.system(), 'linux')
    out = os.path.join(ROOT, 'build', f'JJKConverter-{name}.zip')
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for base, _, files in os.walk(app):
            for f in files:
                p = os.path.join(base, f)
                info = zipfile.ZipInfo.from_file(p, os.path.relpath(p, dist))
                info.compress_type = zipfile.ZIP_DEFLATED
                with open(p, 'rb') as fh:
                    z.writestr(info, fh.read())
    print(out, f'{os.path.getsize(out) / 1e6:.0f} MB')


if __name__ == '__main__':
    main()
