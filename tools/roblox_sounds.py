#!/usr/bin/env python3
"""
Builds assets/jjk/sounds/*.ogg from the Jujutsu Shenanigans Roblox audio IDs.

Each sound event is one or more variants (Minecraft picks one at random); each variant layers clips:
  (id, offset_seconds, speed, start_seconds, gain, length_seconds)
speed follows Roblox PlaybackSpeed (pitch and tempo together), start follows TimePosition; length (optional) cuts a long
clip short with a quick fade, so it ends with the move.
Clips come from Roblox, then the JJS Skill Builder mirror (ossaamm.github.io/audio), then tools/roblox_audio/<id>.ogg|.mp3;
the rest are skipped; an event with nothing left keeps its old file.

  python3 tools/roblox_sounds.py            # download (cached in build/roblox-cache) and write the oggs
"""
import gzip
import json
import os
import sys
import urllib.parse
import urllib.request

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/jjk/sounds")
CACHE = os.path.join(ROOT, "build/roblox-cache")
MANUAL = os.path.join(ROOT, "tools/roblox_audio")
MIRROR = "https://ossaamm.github.io/audio"
RATE = 48000


def c(i, at=0.0, speed=1.0, start=0.0, gain=1.0, length=None):
    return (str(i), at, speed, start, gain, length)


def one(*clips):
    return [list(clips)]


M1_HITS = [[c(8595975878)], [c(8595975878, speed=0.9)], [c(8595975458, speed=1.2)]]

SOUNDS = {
    # --- Shared combat ---
    "swing": one(c(4571259077)),
    "swing_heavy": one(c(4059009185)),
    "hit_light": M1_HITS,
    "hit_heavy": one(c(8595974357)),
    "hit_slam": one(c(3778609188), c(9118615862, gain=0.7)),
    "block": [[c(4306994267), c(9125669515, gain=0.6)], [c(4306994664)], [c(4306994923), c(9116618112, gain=0.6)]],
    "parry": one(c(9125669515), c(9116618112, at=0.03), c(4306994923, gain=0.7)),
    "guard_break": one(c(6737581315)),
    "dash": one(c(4909206080)),
    "side_dash": one(c(3929467229)),
    "ground_impact": [[c(9118615862)], [c(9118614058)], [c(9116816096)], [c(3848076724)]],
    "heavy_charge": one(c(17046377464)),
    # --- Gojo ---
    "infinity_on": one(c(9066732918)),
    "blue_cast": one(c(411286671, speed=0.75)),
    "blue_spawn": one(c(9105467029, start=0.2)),
    "red_charge": one(c(6006851551, speed=1.2)),
    "red_fire": one(c(154787303)),
    "red_explosion": one(c(3059775624, speed=1.4)),
    # "Aka" (79055523886516) is on neither Roblox's public CDN nor the mirror: its own Red clips instead.
    "red_amplified": one(c(79055523886516), c(3059775624, speed=1.1), c(12764933067, at=0.05, gain=0.7)),
    "purple_music": one(c(14326861262)),
    "purple_fusion": one(c(17018019870)),
    "purple_collision": one(c(15075475525)),
    "purple_fire": one(c(698224146)),
    "teleport_out": one(c(9118159096)),
    "teleport_in": one(c(6737581315, gain=0.8)),
    "domain_charge": one(c(6667923288)),
    "domain_expand": one(c(3059775781), c(7260423115), c(15171602676, at=0.4)),
    "uv_music": one(c(16071901783)),
    "domain_collapse": one(c(6737581507)),
    "awaken_grab": one(c(3929467888)),
    "awaken": one(c(4458760518), c(9066673412, at=0.3)),
    "max_blue_wind": one(c(9056932358)),
    "max_blue_absorb": one(c(4299623070), c(9105467029, at=0.2)),
    "max_blue_collapse": one(c(2227416952)),
    "max_red_charge": one(c(16828657609), c(16773286492, gain=0.7)),
    "max_red_fire": one(c(16828657180)),
    "max_red_explosion": one(c(3059775624), c(8595975458, speed=1.2, gain=0.8)),
    "finisher": one(c(12764933067, speed=1.5), c(9114314398), c(17520297840, at=0.1)),
    # --- Domain clash (the JJS domain clash track) ---
    "clash_music": one(c(89526560746434)),
    "clash_start": one(c(3059775781), c(7260423115, at=0.1)),
    "clash_perfect": one(c(12764933067, speed=1.5), c(9114314398, gain=0.6)),
    "clash_hit": M1_HITS,
    "clash_miss": one(c(4306994267)),
    "clash_win": one(c(17520297840), c(6737581507, at=0.2)),
    "clash_beat": one(c(8595975458, speed=1.2, gain=0.35)),
    "clash_countdown": one(c(9125615451)),
    "domain_clash": one(c(3059775781)),
    "domain_block": one(c(9125669515)),
    "domain_surehit": one(c(15171602676)),
    "domain_ambient": one(c(15171602676, gain=0.35)),
    # --- Remaining Gojo sounds ---
    "infinity_off": one(c(9066732918, speed=1.25, gain=0.7)),
    "infinity_ripple": one(c(9066732918, speed=1.4, gain=0.5)),
    "infinity_hold": one(c(9066732918, speed=0.8, gain=0.5)),
    "blue_hum": one(c(411286671, speed=0.75, gain=0.6)),
    "blue_collapse": one(c(2227416952, speed=1.3)),
    "red_compress": one(c(6006851551, speed=1.4)),
    "purple_form": one(c(17018019870, gain=0.7)),
    "purple_travel": one(c(698224146, gain=0.6)),
    "purple_end": one(c(7602599324), c(4776197442, gain=0.8)),
    "max_charge": one(c(4299624634)),
    "max_blue_hum": one(c(9056932358, gain=0.7)),
    "awaken_end": one(c(4458760518, speed=0.8, gain=0.7)),
    "no_energy": one(c(9125669515, speed=1.4, gain=0.4)),
    "teleport": one(c(9118159096)),
    "twofold_swing1": one(c(3755637186)),
    "twofold_swing2": one(c(3755636992)),
    "twofold_hit1": one(c(4086172909)),
    "twofold_hit2": one(c(7515452875)),
    "face_grater_drag": one(c(93186173642807)),
    "face_grater_throw": one(c(82926856324306)),
    "red_max_blackflash": one(c(17284219852), c(17520297840, at=0.3)),
    "unlimited_purple_start": one(c(4858918400), c(4299624634, at=0.4), c(14457960806, at=0.6)),
    "unlimited_purple_explode": one(c(4776197442), c(7602599324, at=0.15)),
    "zero_two_open": one(c(111507747920000), c(135405966044594, at=0.3)),
    "zero_two_music": one(c(16071901783)),
    "zero_two_hit": [[c(130525286637724)], [c(94234054127236)], [c(108671308229639)]],
    "zero_two_barrage": one(c(127027345708590)),
    "zero_two_boost": [[c(93070893390347)], [c(126837281119039)]],
    "zero_two_slowdown": one(c(76523688182264)),
    "zero_two_breathe": one(c(82179939991290)),
    "ragdoll_fall": [[c(3784888301)], [c(3784888809)], [c(3784889529)]],
    # --- Remaining Hakari sounds ---
    "gamble_riichi": one(c(3299794881, speed=0.9), c(16943255415, at=0.2, gain=0.7)),
    "gamble_signal": one(c(3299794881, speed=1.3, gain=0.6)),
    "gamble_stop": one(c(9125669515)),
    "gamble_miss": one(c(6737581507, gain=0.6)),
    "jackpot_heal": one(c(17046377464, speed=1.2, gain=0.6)),
    "jackpot_end": one(c(6737581507)),
    "rhythm_beat": one(c(17046282624, gain=0.6)),
    "idg_ambient": one(c(16943255415, gain=0.3)),
    # --- Hakari ---
    "ball_throw": one(c(4059009185), c(9114427348, at=0.08)),
    "ball_hit": one(c(7512928742)),
    "ball_ricochet": one(c(7512928742, gain=0.6)),
    "shutter_rise": one(c(9125644321), c(858508159, at=0.3)),
    "shutter_slam": one(c(9116684884), c(9118614717)),
    "shutter_divide": one(c(2227416952)),
    "rough_charge": one(c(17046377464)),
    "rough_impact": one(c(4059009185), c(9118614717, at=0.05)),
    "rough_air": one(c(17168364647), c(17046282310, at=0.05), c(120458587618994, at=0.12), c(71472197762839, at=0.15)),
    "fever_kick": one(c(17101065238)),
    "fever_hit": one(c(17101065020)),
    "fever_rush": one(c(17101065425)),
    "fever_break": one(c(6324841214), c(9118614717)),
    "fever_crush": one(c(100606314590244, start=0.15), c(9116684884, at=0.1, start=0.18), c(9118614717, at=0.1), c(3778609188, at=0.2)),
    "door_open": one(c(9125615451), c(9125644321, at=0.1)),
    "door_block": one(c(9116684884)),
    "door_slam": one(c(9126228977), c(6324841214, at=0.08), c(9118614717, at=0.1)),
    "idg_voice": one(c(137100901601126)),
    "idg_sealed": one(c(3059775781), c(7260423115), c(7252480818, at=0.4)),
    "idg_music": one(c(9039704032)),
    "gamble_spin": one(c(3299794881)),
    "gamble_visual": one(c(16943255415)),
    # 6644505962 isn't downloadable; the wiki's upload of the JJS jackpot sound.
    "jackpot": one(c(6644505962), c("wiki:Jackot.mp3")),
    "jackpot_music": one(c(1841443579)),
    "lucky_hit": M1_HITS + [[c(8595974357)]],
    "lucky_final": one(c(8595974357)),
    "rushdown_rush": one(c(3084314259), c(16773286492, gain=0.7)),
    "rushdown_grab": one(c(9105467029)),
    "overwhelm_fist": [[c(3755636992)], [c(3755637186)]],
    "overwhelm_swing": one(c(17046505673), c(17046281380, gain=0.8)),
    "surge_dash": one(c(3929467229), c(17169364965)),
    "surge_hit": [[c(17046282624)], [c(17169365111)], [c(17169365331)]],
    "surge_vanish": one(c(17169364809)),
    "surge_appear": one(c(9119122635)),
    "surge_launch": one(c(9114362943)),
    "rhythm_tick": one(c(17046377464)),
    # --- Yuji / Vessel ---
    "cursed_strikes_start": one(c(16773286492)),
    "cursed_strikes_slide": one(c(3084314259, speed=1.1)),
    "cursed_strikes_hit": one(c(16773286330)),
    "cursed_strikes_spin": one(c(8120249833)),
    "cursed_strikes_impact": one(c(7093763783)),
    "crushing_charge": one(c(4403634269)),
    "crushing_fist": one(c(4571259077)),
    "crushing_impact": one(c(7093763783)),
    "crushing_hit": one(c(7307838125, speed=1.25), c(9118614717, speed=1.25)),
    "divergent_charge": one(c(4403634269), c(4059009185, at=0.25)),
    "divergent": one(c(5795505380, speed=1.5)),
    "divergent_hit": one(c(7515452875)),
    "black_flash": one(c(12764933067, speed=1.5), c(9114314398)),
    "black_flash_chain": one(c(112426502291350)),
    "black_flash_windup": one(c(102672006215074)),
    "black_flash_heavy": one(c(12764933067, speed=1.5), c(12764933067, at=0.05), c(9114314398, at=0.1)),
    "kokusen": one(c(12761286504)),
    "entrusted_music": one(c(93167187278849)),
    "manji_startup": one(c(9125615451)),
    "manji_dodge": one(c(6470740758)),
    "manji_swing": one(c(9126228977)),
    "manji_slam": one(c(9113504593)),
    "manji_crush": one(c(9118614717), c(8120249833, at=0.2), c(3778609188, at=0.55), c(4307207693, at=0.6), c(3848082818, at=0.65)),
    # --- King of Curses ---
    "sukuna_awaken": one(c(15675012262), c(4458760518, at=0.3)),
    "dismantle_slash": one(c(935843979, speed=1.5)),
    "dismantle_finish": one(c(9119749145)),
    "dismantle_spin": one(c(8120249833)),
    "wcs_line": [[c(17053666464)], [c(17053670289)], [c(17053667034)]],
    "wcs_line_1": one(c(17053666464)),
    "wcs_line_2": one(c(17053670289)),
    "wcs_line_3": one(c(17053667034)),
    "open_hands": one(c(1072005487)),
    "open_clap": one(c(6874043782)),
    "open_arrow": one(c(7278163473)),
    "open_idle": one(c(7978653185)),
    "open_fire": one(c(5801273676)),
    "open_explode": one(c(331888892)),
    "rush_start": one(c(3084314259)),
    "rush_hit": one(c(8595975878), c(3763467977, at=0.02)),
    "rush_break": one(c(4086172909), c(8595975878, at=0.02)),
    "rush_slam": one(c(8595975458, speed=1.1), c(7093763783, at=0.03)),
    "shrine_voice": one(c(7817341182)),
    "shrine_expand": one(c(3059775781), c(7260423115)),
    "shrine_ring": one(c(7817336081)),
    "shrine_music": one(c(15583493700)),
    "shrine_ready": one(c(8181034930)),
    "shrine_splash": one(c(9120548819)),
    # --- Yuta / Cursed Partners (the JJS Skill Builder's Cursed Partners list; its "NEW" sets where there are two) ---
    # Swordsmanship M1s: four swings and hits in order (the 4th hit is the shared heavy hit), the downslam and uppercut.
    "yuta_swing_1": one(c(90045339954472)),
    "yuta_swing_2": one(c(101012088379497)),
    "yuta_swing_3": one(c(86863533715011)),
    "yuta_swing_4": one(c(4571259077, speed=1.1), c(75487544146387)),
    "yuta_hit_1": one(c(85608110116893)),
    "yuta_hit_2": one(c(71064172624438)),
    "yuta_hit_3": one(c(119908133360580)),
    "yuta_hit_down": one(c(105620422413126)),
    "yuta_hit_up": one(c(78554756765213)),
    # True Love's steel-cased fists ring on every hit.
    "yuta_metal_hit_1": one(c(113344342875514)),
    "yuta_metal_hit_2": one(c(108742052808505)),
    "yuta_metal_hit_3": one(c(104021116814815)),
    # Shared heavy impacts of his moves and Rika's (VeilstepHit and Impact are on most of them).
    "yuta_heavy_hit": one(c(8595974357), c(90439756216771, gain=0.9)),
    "yuta_slam": one(c(8595974357), c(90439756216771, gain=0.9), c(3778609188, at=0.02, gain=0.8)),
    "yuta_crush": one(c(90439756216771), c(71472197762839, at=0.03), c(3778609188, at=0.05, gain=0.8)),
    # Severing Path and Veilstep
    "severing_start": one(c(95876856890836)),
    "severing_swing_1": one(c(120714154715812)),
    "severing_swing_2": one(c(129682722005190)),
    "severing_swing_3": one(c(73434180515706)),
    "severing_hit_1": one(c(137337069510286)),
    "severing_hit_2": one(c(86231967919037)),
    "severing_hit_3": one(c(97003333926852)),
    "severing_hit_4": one(c(117756899811635)),
    "veilstep": one(c(121354995604661)),
    # Resolute Slash and its Black Flash
    "resolute_grab": one(c(122209095269858)),
    "resolute_leap": one(c(128763907862260)),
    "resolute_swing": one(c(86506412325620)),
    "resolute_slash": one(c(14427790767)),
    "resolute_bf_windup": one(c(85972589629135, start=0.45), c(126598851794168, start=0.4, gain=0.8)),
    "resolute_bf_land": one(c(132879779265336, length=3.5), c(12764933067, speed=1.5)),
    "resolute_bf_finish": one(c(132472285449398), c(12764933067, speed=1.5)),
    # Outburst
    "outburst_start": one(c(115816722667586), c(86752717770401, at=0.3)),
    "outburst_charge": one(c(86752717770401, start=0.3, length=0.9)),
    "outburst_swing": one(c(83164718262698)),
    "outburst_explosion": one(c(83796511190884)),
    "outburst_hit": one(c(82314797872585)),
    "outburst_parry": one(c(74854125236607)),
    # Second Wind
    "second_wind_dash": one(c(122018900711660)),
    "second_wind_grab": one(c(80892604094179)),
    # Rika
    "rika_summon": one(c(81914373674135)),
    "rika_voice": one(c(136734180743874)),
    "rika_move": one(c(94504796493417)),
    "rika_launch": one(c(72050513112942)),
    "rika_haymaker": one(c(92653689754693, at=0.27)),
    "rika_throw_start": one(c(121746815389545, start=0.66)),
    "rika_throw_finish": one(c(91266524334221)),
    # True Love
    "true_love_start": one(c(96210507614652), c(120155303418484), c(87637812190192, at=0.1)),
    "true_love_ring": one(c(82858286583983)),
    "true_love_arm": one(c(82010180737404), c(96857444594355, at=0.6)),
    # Elbow Rush
    "elbow_dash": one(c(76842997698748), c(90282304902568, at=0.22), c(84493401750489, at=0.05)),
    "elbow_hit": one(c(96377525338185), c(9118612945, gain=0.8), c(108742052808505, gain=0.8)),
    "elbow_teleport": one(c(106463408823054)),
    "elbow_barrage": one(c(118844796311667, length=1.1)),
    "elbow_final": one(c(8595974357), c(90439756216771, gain=0.9), c(113344342875514, gain=0.8)),
    # Cursed Speech
    "speech_start": one(c(76485051513534)),
    "speech_dont_move": one(c(91630012293502), c(88080590196375, gain=0.7)),
    "speech_plummet": one(c(128573990774413), c(88080590196375, gain=0.6)),
    "speech_die": one(c(129151959162409), c(88080590196375, gain=0.6)),
    # Energy Ripple and its Fakeout
    "ripple_start": one(c(102285974755522)),
    "ripple_bomb": one(c(73393411828542), c(72879767828886, at=0.1)),
    "fakeout_swing": one(c(86382772357027), c(77514337135425, at=0.03)),
    "fakeout_hit": one(c(128669563112488)),
    "fakeout_burst": one(c(102450195424013, start=0.45), c(129389928559036)),
    # True Love Beam
    "tlb_charge": one(c(82962697738747), c(114265785065488, at=0.1)),
    # tlb_power plays as Rika takes her place (1.9s before the beam): its peak lands on the release.
    "tlb_power": one(c(118689694101518, at=0.26), c(117663808507739, gain=0.55, length=6)),
    "tlb_small": one(c(111645361582005)),
    # Authentic Mutual Love
    "aml_voice": one(c(8098966252)),
    "aml_start": one(c(121842315815683)),
    "aml_music": one(c(84395583379130)),
    "aml_sword_ground": [[c(72417515043142)], [c(95363686354809)], [c(128908669459793)]],
    "aml_pickup": one(c(81157826243193)),
    "aml_run_slash": one(c(93748708419116, start=0.3)),
    "aml_cleave": one(c(935843979)),
    "aml_dismantle": one(c(140388769343703)),
    # Thin Ice Breaker: Uro's opening crack (her other clips aren't mirrored) with the domain's glass.
    "aml_glass": one(c(140527314975641), c(7140152893, at=0.03), c(6737582452, at=0.08, gain=0.7)),
    "aml_clairvoyance": one(c(125025280611426)),
    "aml_mini_rika": one(c(112205395048785), c(80705570218040, at=0.3)),
    "aml_mini_fly": one(c(112981074280224)),
    "jacobs_ladder": one(c(129174925057034, start=1.5)),
}

# New events whose clips can't be fetched borrow an existing sound instead (music has none: it just stays quiet).
FALLBACK = {
    "max_blue_wind": "max_charge", "max_red_charge": "max_charge", "max_red_fire": "red_fire", "shutter_divide": "hit_heavy",
    "rough_air": "rough_impact", "fever_hit": "hit_heavy", "idg_voice": "domain_charge", "rushdown_rush": "fever_rush",
    "rushdown_grab": "hit_heavy", "overwhelm_swing": "swing_heavy", "surge_hit": "lucky_hit", "fever_crush": "fever_break",
    "idg_sealed": "domain_expand", "side_dash": "dash", "awaken_grab": "max_charge", "max_blue_absorb": "max_blue_collapse",
    "overwhelm_fist": "swing_heavy", "surge_dash": "fever_rush", "surge_launch": "ground_impact",
}

# Long tracks: streamed, faded out, and capped (the domain stops them when it ends).
MUSIC = {"aml_music": 46, "shrine_music": 40, "entrusted_music": 30, "uv_music": 80, "idg_music": 80, "jackpot_music": 100, "purple_music": 14, "clash_music": 60, "zero_two_music": 30}


def fetch(i):
    # Hand-supplied files win (most IDs need a logged-in Roblox account to download): tools/roblox_audio/<id>.ogg|.mp3|.wav
    for ext in (".ogg", ".mp3", ".wav", ".flac"):
        own = os.path.join(MANUAL, i + ext)
        if os.path.exists(own):
            return own
    os.makedirs(CACHE, exist_ok=True)
    if i.startswith("wiki:"):
        # A file the JJS wiki hosts (fan uploads of the game's music and voice lines).
        name = i[5:]
        out = os.path.join(CACHE, "wiki_" + name)
        if not os.path.exists(out):
            try:
                api = "https://jujutsu-shenanigans.fandom.com/api.php?action=query&prop=imageinfo&iiprop=url&format=json&titles=File:" + urllib.parse.quote(name)
                req = urllib.request.Request(api, headers={"User-Agent": "Mozilla/5.0"})
                pages = json.load(urllib.request.urlopen(req, timeout=30))["query"]["pages"]
                url = next(iter(pages.values()))["imageinfo"][0]["url"]
                data = urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"}), timeout=90).read()
                open(out, "wb").write(data)
            except Exception as e:
                print(f"  ! {i}: {e}")
                return None
        return out
    out = os.path.join(CACHE, i + ".bin")
    bad = out + ".missing"
    if os.path.exists(out):
        return out
    if os.path.exists(bad):
        return None
    errors = []
    # 1) Roblox itself (works for public, unrestricted audio).
    try:
        meta = json.load(urllib.request.urlopen(f"https://assetdelivery.roblox.com/v2/assetId/{i}", timeout=30))
        data = urllib.request.urlopen(meta["locations"][0]["location"], timeout=90).read()
        if data[:2] == b"\x1f\x8b":
            data = gzip.decompress(data)
        open(out, "wb").write(data)
        return out
    except Exception as e:
        errors.append(f"roblox: {e}")
    # 2) The JJS Skill Builder community's mirror of the game's audio, by the same IDs (ossaamm.github.io).
    for ext in (".mp3", ".ogg", ".wav"):
        try:
            req = urllib.request.Request(f"{MIRROR}/{i}{ext}", headers={"User-Agent": "Mozilla/5.0"})
            data = urllib.request.urlopen(req, timeout=90).read()
            if len(data) > 256:
                open(out, "wb").write(data)
                return out
        except Exception as e:
            errors.append(f"mirror{ext}: {e}")
    open(bad, "w").write("\n".join(errors))
    return None


def load(i, speed, start):
    f = fetch(i)
    if f is None:
        return None
    try:
        d, sr = sf.read(f, always_2d=True)
        d = d.mean(axis=1)
    except Exception:
        # MP3s with ID3 tags (the mirror's files): decode with ffmpeg (pip install imageio-ffmpeg).
        try:
            import subprocess
            import imageio_ffmpeg
            raw = subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-v", "error", "-i", f, "-f", "f32le", "-ac", "1", "-ar", str(RATE), "-"],
                                 capture_output=True, check=True).stdout
            d, sr = np.frombuffer(raw, dtype=np.float32).astype(np.float64), RATE
        except Exception as e:
            print(f"  ! {i}: can't decode ({e})")
            return None
    d = d[int(start * sr):]
    # Speed: play the samples faster (pitch and tempo), i.e. resample to sr/speed and treat as RATE.
    up, down = RATE, int(round(sr * speed))
    g = np.gcd(up, down)
    return resample_poly(d, up // g, down // g)


def build(name, variants):
    made, missing = [], []
    for vi, layers in enumerate(variants):
        parts = []
        for (i, at, speed, start, gain, length) in layers:
            a = load(i, speed, start)
            if a is None:
                missing.append(i)
                continue
            if length is not None and len(a) > length * RATE:
                a = a[:int(length * RATE)].copy()
                fade = min(len(a), int(0.12 * RATE))
                a[-fade:] *= np.linspace(1, 0, fade)
            parts.append((int(at * RATE), a * gain))
        if not parts:
            continue
        n = max(o + len(a) for o, a in parts)
        mix = np.zeros(n)
        for o, a in parts:
            mix[o:o + len(a)] += a
        cap = MUSIC.get(name)
        if cap and len(mix) > cap * RATE:
            mix = mix[:cap * RATE]
            fade = min(len(mix), 3 * RATE)
            mix[-fade:] *= np.linspace(1, 0, fade)
        # Trim trailing silence, normalise the peak.
        nz = np.nonzero(np.abs(mix) > 1e-3)[0]
        if len(nz):
            mix = mix[:nz[-1] + 1]
        peak = np.max(np.abs(mix)) or 1
        mix = mix / peak * 0.9
        fname = name if vi == 0 else f"{name}_{vi + 1}"
        # Chunked: libsndfile's vorbis encoder crashes on one huge write.
        with sf.SoundFile(os.path.join(OUT, fname + ".ogg"), "w", RATE, 1, format="OGG", subtype="VORBIS") as w:
            data = mix.astype(np.float32)
            for k in range(0, len(data), RATE):
                w.write(data[k:k + RATE])
        made.append(fname)
    return made, missing


def main():
    only = set(sys.argv[1:])
    sounds_json = os.path.join(os.path.dirname(OUT), "sounds.json")
    table = json.load(open(sounds_json))
    report = []
    for name, variants in SOUNDS.items():
        if only and name not in only:
            continue
        made, missing = build(name, variants)
        if not made and name not in table and name in FALLBACK:
            table[name] = {"sounds": list(table[FALLBACK[name]]["sounds"])}
            report.append(f"{name:18} FALLBACK {FALLBACK[name]} (missing {','.join(missing)})")
            continue
        if not made and name not in table:
            report.append(f"{name:18} NONE     missing {','.join(missing)}")
            continue
        entry = table.get(name, {"sounds": ["jjk:" + name]})
        if made:
            entry["sounds"] = [{"name": "jjk:" + f, "stream": True} if name in MUSIC else "jjk:" + f for f in made]
        table[name] = entry
        status = "ok" if made and not missing else ("partial" if made else "KEPT OLD")
        report.append(f"{name:18} {status:8} {'missing ' + ','.join(missing) if missing else ''}")
    json.dump(table, open(sounds_json, "w"), indent=2)
    print("\n".join(report))
    unmapped = [k for k in table if k not in SOUNDS]
    if unmapped:
        print("NOT FROM JJS AUDIO:", ", ".join(unmapped))


if __name__ == "__main__":
    main()
