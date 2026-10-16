# PyInstaller spec for the converter app: two programs sharing one folder (JJKConverter: the window; jjk_worker: runs
# the pipeline's scripts). The scripts themselves stay plain .py files under tools/ next to the programs (they find
# their data from their own path); only what they import from Python and the libraries is built in.
#   pyinstaller tools/exe/jjk.spec      (tools/exe/build_exe.py does this and adds the data)
import os, sys
sys.path.insert(0, os.path.join(SPECPATH, '..'))
import make_pc_package as MP

TOOLS = os.path.abspath(os.path.join(SPECPATH, '..'))
OURS = {s[:-3] for s in MP.SCRIPTS}


def own(name):
    return name.split('.')[0] in OURS or name.startswith('r6_')


w = Analysis([os.path.join(SPECPATH, 'worker.py')], pathex=[TOOLS], hiddenimports=sorted(OURS),
             excludes=['tkinter', 'matplotlib', 'IPython', 'pytest'])
w.pure = [x for x in w.pure if not own(x[0])]  # the scripts run from tools/, not from inside the program
g = Analysis([os.path.join(SPECPATH, 'gui.py')], excludes=['torch', 'numpy', 'scipy', 'cv2', 'PIL'])

wexe = EXE(PYZ(w.pure), w.scripts, [], exclude_binaries=True, name='jjk_worker', console=True)
gexe = EXE(PYZ(g.pure), g.scripts, [], exclude_binaries=True, name='JJKConverter', console=False)
COLLECT(wexe, w.binaries, w.datas, gexe, g.binaries, g.datas, name='JJKConverter')
