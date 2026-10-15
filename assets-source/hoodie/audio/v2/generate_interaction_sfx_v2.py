"""Hoodie — efeitos de interação v2 (síntese original, só biblioteca padrão).

O que muda em relação à v1:
- Ronronar audível em alto-falante de celular: em vez de um tom puro em 110/220 Hz (que o
  alto-falante corta), um trem de pulsos rico em harmônicos na faixa 250–900 Hz. O ouvido
  "reconstrói" o grave a partir dos harmônicos (fundamental ausente). Inspiração e expiração
  alternam com tom levemente diferente, como num ronronar estilizado.
- Timbre com corpo: ondas quadradas suaves (poucos harmônicos ímpares), um pouco de vibrato e
  um pequeno deslize de afinação no ataque, em vez de senoide pura.
- Textura de carinho: ruído filtrado bem suave, como mão passando no pelo.
- Variações: três toques e dois carinhos com afinação/tempo diferentes, para repetir sem cansar.
- Volume casado: todos normalizados pelo nível percebido no alto-falante do celular.
Mantém o acorde de Lá maior da v1, ataques e finais sem estalo e nenhum sample de terceiros.
"""

import math
import random
import struct
import wave
from pathlib import Path

RATE = 44100
OUT = Path(__file__).resolve().parent
TAU = math.tau

# Lá maior (as mesmas notas da v1) e algumas oitavas.
E4, A4, CS5, E5, A5, CS6 = 329.628, 440.0, 554.365, 659.255, 880.0, 1108.731


# ───────────── Blocos ─────────────

def env(t, dur, attack=0.012, release=0.06, curve=1.0):
    """Envelope sem estalos: subida em seno², queda exponencial suave até zero no fim."""
    if not 0 <= t < dur:
        return 0.0
    a = math.sin(min(1.0, t / attack) * math.pi / 2) ** 2
    decay = math.exp(-curve * t / max(dur, 1e-6))
    r = math.sin(min(1.0, (dur - t) / release) * math.pi / 2) ** 2
    return a * decay * r


def soft_square(phase, harmonics=4):
    """Quadrada arredondada: só os primeiros harmônicos ímpares (soa 8-bit, mas macia)."""
    return sum(math.sin((2 * k + 1) * phase) / (2 * k + 1) for k in range(harmonics)) * 0.9


class Voice:
    """Uma nota com deslize de afinação no ataque e vibrato leve; fase contínua (sem cliques)."""

    def __init__(self, start, dur, freq, gain, glide=0.0, glide_ms=0.03, vibrato=0.0, harmonics=3, curve=1.2, attack=0.012):
        self.start, self.dur, self.freq, self.gain = start, dur, freq, gain
        self.glide, self.glide_ms, self.vibrato, self.harmonics, self.curve, self.attack = glide, glide_ms, vibrato, harmonics, curve, attack
        self.phase = 0.0

    def sample(self, t):
        local = t - self.start
        if not 0 <= local < self.dur:
            return 0.0
        # Começa [glide] semitons acima/abaixo e assenta na nota (o "boing" fofo).
        bend = self.glide * math.exp(-local / self.glide_ms)
        vib = self.vibrato * math.sin(TAU * 5.5 * local) * min(1.0, local / 0.08)
        f = self.freq * 2 ** ((bend + vib) / 12)
        self.phase += TAU * f / RATE
        return self.gain * soft_square(self.phase, self.harmonics) * env(local, self.dur, self.attack, curve=self.curve)


class Noise:
    """Ruído passa-faixa simples (dois polos), para textura de pelo e transientes."""

    def __init__(self, seed, low, high):
        self.rng = random.Random(seed)
        self.hp = self.lp = self.prev = 0.0
        self.a_lp = 1 - math.exp(-TAU * high / RATE)
        self.a_hp = math.exp(-TAU * low / RATE)

    def next(self):
        x = self.rng.uniform(-1, 1)
        self.lp += self.a_lp * (x - self.lp)
        self.hp = self.a_hp * (self.hp + self.lp - self.prev)
        self.prev = self.lp
        return self.hp


def render(dur, fn):
    return [fn(i / RATE) for i in range(round(dur * RATE))]


def mix(*tracks):
    n = max(map(len, tracks))
    return [sum(tr[i] for tr in tracks if i < len(tr)) for i in range(n)]


# ───────────── Nível percebido no celular ─────────────

def highpass(x, fc):
    w0 = TAU * fc / RATE; a = math.sin(w0) / (2 * 0.707); c = math.cos(w0)
    b0, b1, b2, a0, a1, a2 = (1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + a, -2 * c, 1 - a
    y = []; x1 = x2 = y1 = y2 = 0.0
    for s in x:
        o = (b0 * s + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0
        x2, x1, y2, y1 = x1, s, y1, o; y.append(o)
    return y


def phone_rms(x):
    """RMS depois de simular o alto-falante do celular (corta abaixo de ~350 Hz, 24 dB/oitava)."""
    p = highpass(highpass(x, 350), 350)
    return math.sqrt(sum(s * s for s in p) / len(p))


def normalize(x, target_db, peak_limit_db=-6.0):
    """Casa o volume percebido no celular, sem passar do pico."""
    gain = 10 ** (target_db / 20) / max(phone_rms(x), 1e-9)
    peak = max(map(abs, x)) * gain
    limit = 10 ** (peak_limit_db / 20)
    if peak > limit:
        gain *= limit / peak
    return [s * gain for s in x]


# ───────────── Sons ─────────────

def tap(variant):
    """Toque rápido: 'pop' com deslize para baixo + nota de resposta. Três afinações."""
    shift = [0, 2, -3][variant]
    up = lambda f: f * 2 ** (shift / 12)
    click = Noise(100 + variant, 1500, 6000)
    voices = [
        Voice(0.000, 0.090, up(E5), 0.55, glide=4, glide_ms=0.012, harmonics=2, curve=2.5, attack=0.003),
        Voice(0.055, 0.120, up(A5), 0.40, glide=1.5, glide_ms=0.015, harmonics=2, curve=2.0, attack=0.004),
    ]
    return render(0.19, lambda t: sum(v.sample(t) for v in voices) + 0.18 * click.next() * env(t, 0.012, 0.001, 0.010))


def fur(seed, dur, strokes, gain):
    """Mão passando no pelo: ruído suave em ondas (uma por passada)."""
    n = Noise(seed, 1200, 5000)
    def f(t):
        stroke = 0.5 - 0.5 * math.cos(TAU * strokes * t / dur)
        return gain * n.next() * stroke * env(t, dur, 0.05, 0.12, curve=0.3)
    return render(dur, f)


def pet_head(variant):
    """Início do carinho: tríade subindo com vibrato leve + passadas no pelo."""
    rest = [0.0, 0.03][variant]
    voices = [
        Voice(0.00, 0.30, E4 * 2, 0.36, glide=-2, vibrato=0.12),
        Voice(0.15 + rest, 0.30, A4 * 2, 0.32, glide=-2, vibrato=0.12),
        Voice(0.30 + rest * 2, 0.34, CS5 * 2, 0.30, glide=-2, vibrato=0.15),
    ]
    dur = max(v.start + v.dur for v in voices) + 0.01
    melody = render(dur, lambda t: sum(v.sample(t) for v in voices))
    return mix(melody, fur(200 + variant, dur, 2, 0.06))


def purr(seconds=1.2, seed=20261015):
    """Ronronar estilizado com harmônicos na faixa do alto-falante do celular."""
    rng = random.Random(seed)
    breath_noise = Noise(seed, 300, 1800)
    phase = 0.0
    jitter = 0.0

    def f(t):
        nonlocal phase, jitter
        # Duas fases por ciclo (inspira/expira) a ~1,6 Hz; a expiração é um pouco mais grave e forte.
        cycle = (t * 1.6) % 1.0
        exhale = cycle >= 0.45
        jitter += 0.002 * (rng.uniform(-1, 1) - jitter)
        rate = (24.0 if exhale else 27.0) * (1 + jitter)
        phase += TAU * rate / RATE
        # Pulso curto a cada batida do "motor": ressoa em ~420 Hz e ~780 Hz (audível no celular).
        local = (phase % TAU) / TAU / rate
        ping = math.exp(-local * 110) * (0.75 * math.sin(TAU * 420 * local) + 0.35 * math.sin(TAU * 780 * local)
                                       + 0.25 * math.sin(TAU * 260 * local))
        swell = 0.55 + 0.45 * math.sin(math.pi * ((cycle - 0.45) / 0.55 if exhale else cycle / 0.45))
        level = (1.0 if exhale else 0.7) * swell
        return (ping + 0.10 * breath_noise.next()) * level * env(t, seconds, 0.10, 0.18, curve=0.2)

    return render(seconds, f)


def nuzzle():
    """Finalização afetuosa: tríade descendo, macia, e um 'boop' curtinho subindo no fim."""
    voices = [
        Voice(0.00, 0.28, CS5, 0.34, glide=1, vibrato=0.10),
        Voice(0.16, 0.30, A4, 0.34, glide=1, vibrato=0.10),
        Voice(0.32, 0.30, E4 * 2, 0.32, glide=1, vibrato=0.12),
        Voice(0.56, 0.16, A5, 0.26, glide=-5, glide_ms=0.02, harmonics=2, curve=2.0, attack=0.004),
    ]
    melody = render(0.74, lambda t: sum(v.sample(t) for v in voices))
    return mix(melody, fur(300, 0.5, 1, 0.045))


# ───────────── Saída ─────────────

def crossfade(a, b, ms=60):
    n = round(ms / 1000 * RATE)
    n = min(n, len(a), len(b))
    head, tail = a[:-n], a[-n:]
    blend = [tail[i] * (1 - i / n) + b[i] * (i / n) for i in range(n)]
    return head + blend + b[n:]


def write(name, samples):
    peak = max(map(abs, samples))
    assert 0 < peak < 0.6, (name, peak)
    assert abs(samples[0]) < 0.01 and abs(samples[-1]) < 0.01, f"{name}: borda com estalo"
    pcm = b"".join(struct.pack("<h", round(max(-1.0, min(1.0, x)) * 32767)) for x in samples)
    with wave.open(str(OUT / name), "wb") as audio:
        audio.setnchannels(1); audio.setsampwidth(2); audio.setframerate(RATE)
        audio.writeframes(pcm)
    print(f"{name}: {len(samples) / RATE:.3f}s, pico {20 * math.log10(peak):.1f} dBFS, "
          f"no celular {20 * math.log10(phone_rms(samples)):.1f} dBFS")


def main():
    target = -24.0
    taps = [normalize(tap(v), target) for v in range(3)]
    pets = [normalize(pet_head(v), target) for v in range(2)]
    purr_ = normalize(purr(), target - 2)
    nuzzle_ = normalize(nuzzle(), target)
    full = crossfade(crossfade(pets[0], purr_), nuzzle_)

    for i, s in enumerate(taps):
        write(f"hoodie_tap_{i + 1}.wav", s)
    for i, s in enumerate(pets):
        write(f"hoodie_pet_head_{i + 1}.wav", s)
    write("hoodie_purr.wav", purr_)
    write("hoodie_nuzzle.wav", nuzzle_)
    write("hoodie_petting_full.wav", full)
    gap = [0.0] * round(0.5 * RATE)
    write("hoodie_interactions_preview_v2.wav", taps[0] + gap + taps[1] + gap + taps[2] + gap + full)

    # Comparação A/B com a v1 (se os arquivos dela estiverem na pasta acima): v1 e depois v2, com 1 s entre os blocos.
    v1 = OUT.parent
    if (v1 / "hoodie_tap.wav").exists():
        def load(path):
            with wave.open(str(path)) as w:
                return [v / 32768 for v in struct.unpack("<%dh" % w.getnframes(), w.readframes(w.getnframes()))]
        pause = [0.0] * RATE
        ab = (load(v1 / "hoodie_tap.wav") + gap + taps[0] + pause
              + load(v1 / "hoodie_purr.wav") + gap + purr_ + pause
              + load(v1 / "hoodie_petting_full.wav") + gap + full)
        write("comparacao_v1_v2.wav", ab)


if __name__ == "__main__":
    main()
