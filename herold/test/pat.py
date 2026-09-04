#!/usr/bin/env python3
"""Pulsankunftszeit (PAT) aus einer Herold-EKG-Mitschrift.

Liest ekg.csv (phase,lead,mv,ppg,seq): 500 Hz EKG, PPG darin mit 100 Hz
(jeder fuenfte Wert echt, sonst -1). Sucht R-Zacken im EKG und je Schlag den
Fuss der PPG-Welle (Minimum vor dem Anstieg) und gibt den Abstand aus.

Keine mmHg. PAT ist eine Laufzeit in Millisekunden - sie aendert sich mit dem
Blutdruck, ist aber kein Blutdruck (Vorauswurfphase, +-15 mmHg).
"""
import sys, csv, math

def lies(pfad):
    ekg, ppg = [], []
    with open(pfad) as f:
        r = csv.reader(f); kopf = next(r)
        hat_ppg = "ppg" in kopf
        for z in r:
            if len(z) < 3 or z[0] != "MESSEN": continue
            try: mv = float(z[2])
            except: continue
            ekg.append(mv)
            p = None
            if hat_ppg and len(z) > 3 and z[3] not in ("None", "-1", ""):
                try: p = int(float(z[3]))
                except: p = None
            ppg.append(p)
    return ekg, ppg, hat_ppg

def biquad(fc, fs, hochpass=False):
    w = 2*math.pi*fc/fs; c = math.cos(w); al = math.sin(w)/math.sqrt(2); a0 = 1+al
    if hochpass: b0=(1+c)/2/a0; b1=-(1+c)/a0; b2=(1+c)/2/a0
    else:        b0=(1-c)/2/a0; b1=(1-c)/a0;  b2=(1-c)/2/a0
    a1=-2*c/a0; a2=(1-al)/a0
    x1=x2=y1=y2=0.0
    def step(x):
        nonlocal x1,x2,y1,y2
        y=b0*x+b1*x1+b2*x2-a1*y1-a2*y2; x2=x1; x1=x; y2=y1; y1=y; return y
    return step

def r_zacken(ekg, fs=500):
    hp = biquad(5, fs, True); tp = biquad(25, fs)
    band = [tp(hp(v)) for v in ekg]
    huelle = []; fenster = int(0.08*fs); q = [0.0]*fenster; s = 0.0; i = 0
    for v in band:
        e = v*v; s += e - q[i]; q[i] = e; i = (i+1) % fenster; huelle.append(s/fenster)
    sortiert = sorted(huelle); schwelle = sortiert[int(len(sortiert)*0.9)]
    peaks = []; sperre = 0
    for k in range(1, len(huelle)-1):
        if sperre > 0: sperre -= 1; continue
        if huelle[k] > schwelle and huelle[k] >= huelle[k-1] and huelle[k] > huelle[k+1]:
            # auf das eigentliche Maximum des Rohsignals in +-40 ms schieben
            a, b = max(0,k-20), min(len(ekg)-1,k+20)
            kk = max(range(a,b+1), key=lambda j: ekg[j])
            peaks.append(kk); sperre = int(0.35*fs)
    return peaks

def ppg_fuss(ppg, r, fs=500):
    """Minimum des PPG zwischen R und R+420 ms, gefolgt von einem Anstieg."""
    a, b = r, min(len(ppg)-1, r + int(0.42*fs))
    kand = [(j, ppg[j]) for j in range(a, b+1) if ppg[j] is not None]
    if len(kand) < 6: return None
    jmin, vmin = min(kand, key=lambda t: t[1])
    nach = [v for j, v in kand if j > jmin]
    if not nach or max(nach) - vmin < 3: return None      # kein Anstieg -> kein Fuss
    return jmin

def main(pfad):
    ekg, ppg, hat = lies(pfad)
    print(f"Proben: {len(ekg)} ({len(ekg)/500:.1f} s), PPG-Spalte: {hat}, echte PPG-Werte: {sum(1 for p in ppg if p is not None)}")
    if not hat: print("Kein PPG in dieser Mitschrift - EKG neu aufnehmen."); return
    rs = r_zacken(ekg)
    print(f"R-Zacken: {len(rs)}")
    pats = []
    for r in rs:
        f = ppg_fuss(ppg, r)
        if f is not None:
            pat = (f - r) * 1000 / 500
            if 120 <= pat <= 450: pats.append(pat)
    if len(pats) < 5: print("Zu wenig brauchbare Schlaege:", len(pats)); return
    pats.sort(); n = len(pats)
    med = pats[n//2]; q1 = pats[n//4]; q3 = pats[3*n//4]
    print(f"PAT: Median {med:.0f} ms, IQR {q1:.0f}-{q3:.0f} ms, n={n} von {len(rs)} Schlaegen")

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "doku/ekg.csv")
