#!/usr/bin/env python3
"""Cuts the domain cinematic down to a vertical 1080x1920 YouTube Short (under a minute).

Usage: python3 tools/make_domain_short.py FILM.mp4 SOUNDTRACK.wav OUT.mp4

FILM and SOUNDTRACK are the silent film and its mix (make_domain_cinematic.py / make_domain_soundtrack.py). The Short is
the hook, the whole disclaimer on one card, then a cut of each scene's best seconds; the 16:9 picture sits over a blurred
copy of itself, and the music is cut from the same mix at the same times.
"""
import subprocess
import sys
import types

import imageio_ffmpeg
import numpy as np
import soundfile as sf

import make_domain_cinematic as M

FF = imageio_ffmpeg.get_ffmpeg_exe()
W, H, FPS = 1080, 1920, 60
CARD = 19.0
CROP_W, CROP_H, CROP_Y = 332, 590, 65     # 9:16 inside the 1280x720 picture, between its letterbox bars
TITLE_AT = 7.88
# (start, end, pan from x, pan to x): the film's seconds and where in the picture the 9:16 window starts and ends. The
# window sweeps across the action instead of sitting still, so the Short fills the screen with no blur above or below.
CLIPS = [(0.0, TITLE_AT, 380, 560), (TITLE_AT, 9.78, None, None), (38.3, 41.5, 440, 520), (43.8, 47.0, 400, 560), (49.2, 51.8, 440, 520),
         (54.8, 57.6, 300, 600), (59.9, 67.4, 80, 880), (77.9, 83.1, 100, 860), (92.5, 96.9, 560, 560)]


def title(path):
    rec = types.SimpleNamespace(w=W, h=W)
    p = subprocess.Popen([FF, '-y', '-v', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', '%dx%d' % (W, H), '-r', str(FPS), '-i', '-',
                          '-c:v', 'libx264', '-preset', 'medium', '-crf', '20', '-pix_fmt', 'yuv420p', path], stdin=subprocess.PIPE)
    pad = (H - W) // 2
    for f in M.title_part(rec, M.HOOK_TITLE, 9.78 - TITLE_AT, FPS):
        full = np.zeros((H, W, 3), np.uint8)
        full[pad:pad + W] = f.clip(0, 255).astype(np.uint8)
        p.stdin.write(full.tobytes())
    p.stdin.close()
    p.wait()


def card(path):
    rec = types.SimpleNamespace(w=W, h=H)
    paras = [(M.CARDS[0][1][0][0], 0.6), (M.CARDS[1][1][0][0], 5.0), (M.CARDS[1][1][1][0], 9.5)]
    p = subprocess.Popen([FF, '-y', '-v', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', '%dx%d' % (W, H), '-r', str(FPS), '-i', '-',
                          '-c:v', 'libx264', '-preset', 'medium', '-crf', '23', '-pix_fmt', 'yuv420p', path], stdin=subprocess.PIPE)
    for f in M.card_part(rec, CARD, paras, FPS, size=46):
        p.stdin.write(f.clip(0, 255).astype(np.uint8).tobytes())
    p.stdin.close()
    p.wait()


def main():
    film, wav, out = sys.argv[1:4]
    card('/tmp/short_card.mp4')
    title('/tmp/short_title.mp4')
    parts, order = [], []
    for i, (a, b, x0, x1) in enumerate(CLIPS):
        if x0 is None:
            order.append('[1:v]fps=%d,format=yuv420p[t]' % FPS)
            order.append('[t]')
            continue
        parts.append("[0:v]trim=%.3f:%.3f,setpts=PTS-STARTPTS,crop=%d:%d:'%d+(%d)*t/%.3f':%d,scale=%d:%d:flags=lanczos,fps=%d,format=yuv420p[v%d]"
                     % (a, b, CROP_W, CROP_H, x0, x1 - x0, b - a, CROP_Y, W, H, FPS, i))
        order.append('[v%d]' % i)
        if i == 1 - 1 + 0 and False:
            pass
    graph = ';'.join(parts + [o for o in order if o.startswith('[1:v')]) + ';[2:v]fps=%d,format=yuv420p[c]' % FPS
    seq = ''.join(o for o in order if not o.startswith('[1:v'))
    seq = seq.replace('[t]', '[t]', 1)
    # order: hook crop, title, card, highlights
    names = ['[v0]', '[t]', '[c]'] + ['[v%d]' % i for i in range(2, len(CLIPS))]
    graph += ';' + ''.join(names) + 'concat=n=%d:v=1:a=0[v]' % len(names)
    sound, sr = sf.read(wav)
    chunks = []
    for i, (a, b, _x0, _x1) in enumerate(CLIPS):
        seg = sound[int(a * sr):int(b * sr)].copy()
        k = int(0.08 * sr)
        seg[:k] *= np.linspace(0, 1, k)[:, None]
        seg[-k:] *= np.linspace(1, 0, k)[:, None]
        chunks.append(seg)
        if i == 1:
            bed = sound[int(14 * sr):int((14 + CARD) * sr)].copy()
            k = int(1.5 * sr)
            bed[:k] *= np.linspace(0, 1, k)[:, None]
            bed[-k:] *= np.linspace(1, 0, k)[:, None]
            chunks.append(bed)
    sf.write('/tmp/short.wav', np.concatenate(chunks), sr)
    subprocess.run([FF, '-y', '-v', 'error', '-i', film, '-i', '/tmp/short_title.mp4', '-i', '/tmp/short_card.mp4', '-i', '/tmp/short.wav', '-filter_complex', graph, '-map', '[v]', '-map', '3:a',
                    '-c:v', 'libx264', '-preset', 'slow', '-crf', '24', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '192k', '-movflags', '+faststart', '-shortest', out], check=True)


if __name__ == '__main__':
    main()
