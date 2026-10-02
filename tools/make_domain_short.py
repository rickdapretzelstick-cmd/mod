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
# (start, end) in the film's seconds: the hook and title, then the best of each scene.
CLIPS = [(0.0, 9.78), (38.3, 41.5), (43.8, 47.0), (49.2, 51.8), (54.8, 57.6), (59.9, 67.4), (77.9, 83.1), (92.5, 96.9)]


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
    n = len(CLIPS)
    parts = []
    for i, (a, b) in enumerate(CLIPS):
        parts.append('[0:v]trim=%.3f:%.3f,setpts=PTS-STARTPTS,split[a%d][b%d];[a%d]scale=%d:%d:force_original_aspect_ratio=increase,crop=%d:%d,boxblur=30:5,eq=brightness=-0.15[bg%d];'
                     '[b%d]scale=%d:-2[fg%d];[bg%d][fg%d]overlay=0:(H-h)/2,fps=%d[v%d]' % (a, b, i, i, i, W, H, W, H, i, i, W, i, i, i, FPS, i))
    order = '[v0]' + '[c]' + ''.join('[v%d]' % i for i in range(1, n))
    graph = ';'.join(parts) + ';[1:v]fps=%d,format=yuv420p[c];[v0][c]' % FPS + ''.join('[v%d]' % i for i in range(1, n)) + 'concat=n=%d:v=1:a=0[v]' % (n + 1)
    sound, sr = sf.read(wav)
    chunks = []
    for i, (a, b) in enumerate(CLIPS):
        seg = sound[int(a * sr):int(b * sr)].copy()
        k = int(0.08 * sr)
        seg[:k] *= np.linspace(0, 1, k)[:, None]
        seg[-k:] *= np.linspace(1, 0, k)[:, None]
        chunks.append(seg)
        if i == 0:
            bed = sound[int(14 * sr):int((14 + CARD) * sr)].copy()
            k = int(1.5 * sr)
            bed[:k] *= np.linspace(0, 1, k)[:, None]
            bed[-k:] *= np.linspace(1, 0, k)[:, None]
            chunks.append(bed)
    sf.write('/tmp/short.wav', np.concatenate(chunks), sr)
    subprocess.run([FF, '-y', '-v', 'error', '-i', film, '-i', '/tmp/short_card.mp4', '-i', '/tmp/short.wav', '-filter_complex', graph, '-map', '[v]', '-map', '2:a',
                    '-c:v', 'libx264', '-preset', 'slow', '-crf', '24', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '192k', '-movflags', '+faststart', '-shortest', out], check=True)


if __name__ == '__main__':
    main()
