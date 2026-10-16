# jjk_worker.exe: runs one of the converter's scripts with the Python that is built into the app, so the scripts can
# call each other (sys.executable SCRIPT.py ...) and start worker processes exactly as they do under a normal Python.
#   jjk_worker.exe tools/r6_batch.py VIDEO NAME ...
import sys, os, runpy, types, multiprocessing
import multiprocessing.spawn

ROOT = os.path.dirname(sys.executable) if getattr(sys, 'frozen', False) else \
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
TOOLS = os.path.join(ROOT, 'tools')


def _streams():
    for s in (sys.stdout, sys.stderr):  # logs in UTF-8 whatever the Windows code page, a line at a time
        if s is not None:
            try:
                s.reconfigure(encoding='utf-8', errors='replace', line_buffering=True)
            except (AttributeError, ValueError):
                pass


def _worker_main(script):
    """A worker process of `script`'s pool: its functions loaded as __main__ (without running its main block), so the
    jobs the parent sends by name can be found. Windows sends no script path to a frozen app's workers, hence
    R6_SCRIPT, which the parent set."""
    sys.path.insert(0, os.path.dirname(script))
    mod = types.ModuleType('__mp_main__')
    mod.__file__ = script
    mod.__builtins__ = __builtins__
    sys.modules['__main__'] = sys.modules['__mp_main__'] = mod
    with open(script, encoding='utf-8') as f:
        exec(compile(f.read(), script, 'exec'), mod.__dict__)


def main():
    _streams()
    if os.environ.get('R6_TEST_WINEXE') == '1':  # test the Windows path here: workers get no script path
        multiprocessing.spawn.WINEXE = True
    if multiprocessing.spawn.is_forking(sys.argv) and os.environ.get('R6_SCRIPT'):
        _worker_main(os.environ['R6_SCRIPT'])
    multiprocessing.freeze_support()  # a worker (or multiprocessing's own helper) runs here and exits; else returns
    if len(sys.argv) < 2:
        sys.exit('usage: jjk_worker SCRIPT.py [ARGS...]')
    script = sys.argv[1]
    if not os.path.exists(script) and os.path.exists(os.path.join(TOOLS, script)):
        script = os.path.join(TOOLS, script)
    script = os.path.abspath(script)
    os.environ['R6_SCRIPT'] = script
    sys.argv = [script] + sys.argv[2:]
    sys.path.insert(0, os.path.dirname(script))
    runpy.run_path(script, run_name='__main__')


if __name__ == '__main__':
    main()
