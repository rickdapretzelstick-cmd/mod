#!/usr/bin/env python3
"""
One character's animation pass, shared by tools/<character>_anims.py (Yuji, Yuta, Rika...): each clip is refined in
place from its original, timed to the server's hits (tools/hit_timing.py reads them from the move traces):

    p = Pass('yuta', 'Yuta animation pass', 'tools/yuta_anims.py (...)')
    p.guard()                                    # refuse clips this pass already refined
    p.refine('yuta_light_1', hit=100)            # the blow moved onto the server's hit at 100 ms
    p.refine('yuta_rika_throw_hit', hit=0, prev='yuta_rika_throw')   # lands as it starts: wound up in the clip before
    p.refine('yuta_heavy_charge', alive=BREATHE) # a held pose breathes

See refine() for the rest (wind-up, hit-stop, follow-through, the body behind the blow, remaps for multi-hit clips).
"""
import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from anim_kit import ClipData, end_pose, pose_at  # noqa: E402
import anim_quality  # noqa: E402

ANIMS = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/assets/jjk/animations')
LEAD = 80  # ms of travel into a blow the server lands as its clip starts
BREATHE = dict(period=500, cycles=2, amp=0.8)


class Pass:
    def __init__(self, group, tag, authored):
        """{tag}: marks the clips this pass wrote (in their "authored"); {authored}: the whole "authored" text."""
        self.group, self.tag, self.authored = group, tag, authored
        self.dir = os.path.join(ANIMS, group)

    def guard(self):
        """The refinements stack: run only on the originals, never on clips this pass already wrote."""
        done = [f for f in glob.glob(os.path.join(self.dir, '*.json')) if self.tag in json.load(open(f)).get('authored', '')]
        if done:
            raise SystemExit(f'{len(done)} {self.group} clips are already refined. Restore the originals first (from the commit '
                             f'before the {self.tag}), into the working tree only: git restore --source=<commit> --worktree -- '
                             f'src/main/resources/assets/jjk/animations/{self.group} (git checkout <commit> -- <path> also '
                             'stages them, and the next commit would revert the pass)')

    def arrival(self, name):
        """Where the clip's blow lands as authored: the key that ends the striking limb's fastest stretch."""
        c = anim_quality.Clip(os.path.join(self.dir, name + '.json'))
        moving = [b for b in anim_quality.LIMBS if b in c.tracks]
        peak = {b: max(c.speeds(b), key=lambda p: p[1]) for b in moving}
        strike = max(peak, key=lambda b: peak[b][1])
        t_peak = peak[strike][0]
        return next((t for t, _, _ in c.tracks[strike] if t >= t_peak - 10), t_peak)

    def refine(self, name, hit=None, prev=None, old=None, wind=None, hold=60, over=60, body=None, core=None, alive=None,
               overlap=True, remap=None, extend=None, lag=None, lead=None, edit=None, **extra):
        """
        hit      when the server lands the blow, ms from the clip's start: 0 with {prev} = as the clip starts, wound up in
                 {prev} (the clip's own wind-up goes; it travels LEAD ms into the blow from {prev}'s last pose); otherwise
                 the blow at {old} (default: where it was drawn) is retimed onto it. 'auto': no server hit, the blow
                 stays where it was drawn
        wind     (lead ms, k): a pull-back before the blow (default for retimed blows; False: none)
        hold     ms the blow is held (hit-stop); over: ms of follow-through past it
        body     (t_wind, t_contact, t_settle, {twist, lean, crouch, head}): the body behind the blow; (None,) = around it
        core     scales the body's own motion; alive: a held pose breathes (period, cycles, amp, tremble)
        remap    [(old ms, new ms)...]: a piecewise retime first (a flurry's punches onto the server's hits)
        extend   ms added on the last pose first (room to settle a blow drawn onto the clip's last key)
        lead     (ms, prev[, at ms]): the clip first travels {ms} from {prev}'s last pose (or its pose at {at}, when
                 this clip takes over before it ends) into its own first; {old} counts in the clip as drawn
        edit     a function given the clip first, for a change no option covers
        lag      {bone: ms}: those bones' keys on the way into the blow arrive that much later (more whip than the
                 overlap pass leaves room for)
        extra    written into the clip (motion='transition', style='effortless', windup=..., strike='elbow')
        """
        c = ClipData(self.group, name)
        if edit:
            edit(c)
        if extend:
            c.extend(extend)
        if remap:
            c.remap(remap)
        if lead:
            c.lead_in(lead[0], pose_at(self.group, lead[1], lead[2]) if len(lead) > 2 else end_pose(self.group, lead[1]))
        contact = None
        if hit == 'auto':
            hit = self.arrival(name)
        if hit is not None:
            old = (self.arrival(name) if old is None else old) + (lead[0] if lead else 0)
            if hit == 0 and prev:
                c.cut(old, LEAD, prev)
                contact = LEAD
                extra.setdefault('windup', prev)
            else:
                c.retime(old, hit)
                contact = hit
                # A wind-up inside the clip: pulled back before the blow (as long as the way in allows).
                # (A kick that sweeps from the leg drawn back to overhead is already as wound as a leg gets: wind=False.)
                if wind is None and hit >= 60:
                    wind = (min(110, hit * 0.7), 0.35)
        if body == (None,):
            body = (max(0, contact - 80), contact, contact + 220, dict(twist=18, lean=10, crouch=0.8))
        if body:
            c.body(*body[:3], **body[3] if len(body) > 3 else {})
        if core:
            c.core(core)
        if contact is not None:
            if wind:
                c.wind(contact, lead=wind[0], k=wind[1])
            if hold:
                c.hold(contact, hold)
            if over:
                c.overshoot(contact, contact + hold + over)
            c.mark(contact)
        if overlap:
            c.overlap()
        if lag and contact is not None:
            for bone, ms in lag.items():
                for ch, tr in c.tracks.get(bone, {}).items():
                    # (the keys arriving at the blow, after the overlap pass's own lag; never past the key after)
                    out = []
                    for i, (t, v, e) in enumerate(tr):
                        if 0 < t <= contact + 30:
                            nxt = tr[i + 1][0] if i + 1 < len(tr) else t + ms + 5
                            t = min(t + ms, nxt - 5)
                        out.append((t, v, e))
                    c.tracks[bone][ch] = out
        if alive:
            c.alive(**alive)
        c.save(self.authored, **extra)
