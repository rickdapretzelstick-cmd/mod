# Worker pools that work the same on Linux, macOS and Windows. Linux forks (fast, nothing re-imported); Windows (and
# R6_START_METHOD=spawn, to test that path here) starts fresh worker processes, so jobs must carry everything they
# need in their arguments, never in globals set by the parent after start-up.
import os
import multiprocessing as mp


def method():
    want = os.environ.get('R6_START_METHOD', 'fork')
    return want if want in mp.get_all_start_methods() else 'spawn'


def Pool(n):
    if os.name == 'nt':  # Windows can't wait on more than 61 worker processes
        n = min(n, 61)
    return mp.get_context(method()).Pool(n)
