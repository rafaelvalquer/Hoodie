"""Original soft retro interaction sounds. Python standard library only."""

import math
import random
import struct
import wave
from pathlib import Path

RATE = 44100
OUT = Path(__file__).resolve().parent
TAU = math.tau


def envelope(t, duration, attack=0.025, release=0.055):
    if not 0 <= t < duration:
        return 0.0
    a = math.sin(min(1.0, t / attack) * math.pi / 2) ** 2
    r = math.sin(min(1.0, (duration - t) / release) * math.pi / 2) ** 2
    return a * r


def tone(t, frequency):
    # Rounded chiptune timbre: mostly sine, with a little triangle-like warmth.
    p = TAU * frequency * t
    return 0.86 * math.sin(p) - 0.10 * math.sin(3 * p) / 9 + 0.04 * math.sin(5 * p) / 25


def notes(t, sequence):
    value = 0.0
    for start, duration, frequency, gain in sequence:
        local = t - start
        if 0 <= local < duration:
            value += gain * tone(local, frequency) * envelope(local, duration)
    return value


def render(duration, sampler):
    return [sampler(i / RATE) for i in range(round(duration * RATE))]


def write(name, samples):
    peak = max(map(abs, samples), default=0)
    assert 0 < peak < 0.8, (name, peak)
    pcm = b"".join(struct.pack("<h", round(x * 32767)) for x in samples)
    with wave.open(str(OUT / name), "wb") as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(RATE)
        audio.writeframes(pcm)
    rms = math.sqrt(sum(x * x for x in samples) / len(samples))
    print(f"{name}: {len(samples) / RATE:.3f}s, peak {20 * math.log10(peak):.1f} dBFS, RMS {20 * math.log10(rms):.1f} dBFS")


def main():
    tap = render(0.18, lambda t: notes(t, [
        (0.0, 0.115, 659.255, 0.18),
        (0.065, 0.115, 880.0, 0.14),
    ]))
    pet = render(0.6, lambda t: notes(t, [
        (0.0, 0.28, 329.628, 0.16),
        (0.16, 0.29, 440.0, 0.14),
        (0.32, 0.28, 554.365, 0.12),
    ]))

    rng = random.Random(20261015)
    filtered = 0.0

    def purr_sample(t):
        nonlocal filtered
        filtered += 0.04 * (rng.uniform(-1, 1) - filtered)
        # A deliberately stylized motor-like purr, without trying to imitate a real cat.
        pulse = 0.4 + 0.6 * (0.5 + 0.5 * math.sin(TAU * 25 * t)) ** 2
        breath = 0.82 + 0.18 * math.sin(TAU * 1.6 * t)
        body = 0.115 * tone(t, 110) + 0.038 * tone(t, 220)
        return (body + 0.025 * filtered) * pulse * breath * envelope(t, 1.2, 0.10, 0.14)

    purr = render(1.2, purr_sample)
    nuzzle = render(0.7, lambda t: notes(t, [
        (0.0, 0.30, 554.365, 0.12),
        (0.18, 0.32, 440.0, 0.12),
        (0.36, 0.34, 329.628, 0.15),
    ]))
    full = pet + purr + nuzzle
    silence = [0.0] * round(0.55 * RATE)
    for name, samples in [
        ("hoodie_tap.wav", tap),
        ("hoodie_pet_head.wav", pet),
        ("hoodie_purr.wav", purr),
        ("hoodie_nuzzle.wav", nuzzle),
        ("hoodie_petting_full.wav", full),
        ("hoodie_interactions_preview.wav", tap + silence + full),
    ]:
        write(name, samples)


if __name__ == "__main__":
    main()
