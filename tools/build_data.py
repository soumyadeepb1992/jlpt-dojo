"""Build vocab.json and kanji.json assets for the JLPT app.
Usage: python3 -I build_data.py <download_dir> <assets_out_dir>
"""
import csv, json, re, sys, os, collections

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)

def hira(s):
    return ''.join(chr(ord(c) - 0x60) if 'ァ' <= c <= 'ヶ' else c for c in s)

def is_kana(c):
    return '぀' <= c <= 'ヿ'

def is_kanji(c):
    return '一' <= c <= '鿿' or c == '々'

PAREN = re.compile(r'[（(][^）)]*[）)]')

def clean(s):
    s = PAREN.sub('', s)
    s = s.replace('〜', '～')
    s = s.split(';')[0].split('；')[0].split('、')[0]
    return re.sub(r'\s+', '', s).strip()

def clean_meaning(m):
    m = re.sub(r'\s+', ' ', m).strip().strip(',').strip()
    return m

# ---------------- vocab ----------------
vocab = []
seen = set()
for lvl in [5, 4, 3, 2, 1]:
    with open(os.path.join(src, f'jam_n{lvl}.csv'), encoding='utf-8') as f:
        for r in csv.DictReader(f):
            w = clean(r['expression'])
            rd = hira(clean(r['reading']))
            m = clean_meaning(r['meaning'])
            if not w or not rd or not m:
                continue
            if not all(is_kana(c) or is_kanji(c) or c in '～ー' or c.isascii() for c in w):
                continue
            key = (w, rd)
            if key in seen:
                continue
            seen.add(key)
            vocab.append({'w': w, 'r': rd, 'm': m, 'l': lvl})

# ---------------- kanji ----------------
kd = json.load(open(os.path.join(src, 'kanji.json'), encoding='utf-8'))

def kun_stems(k):
    res = set()
    for r in k.get('readings_kun') or []:
        r = r.replace('-', '')
        stem = r.split('.')[0]
        if stem:
            res.add(stem)
    return res

def on_list(k):
    return {hira(r.replace('-', '')) for r in (k.get('readings_on') or []) if r.replace('-', '')}

DAKU = dict(zip('かきくけこさしすせそたちつてとはひふへほ', 'がぎぐげござじずぜぞだぢづでどばびぶべぼ'))
HAN = dict(zip('はひふへほ', 'ぱぴぷぺぽ'))

def variants(r, first, last):
    out = {r}
    if not first and r[0] in DAKU:
        out.add(DAKU[r[0]] + r[1:])
        if r[0] in 'ちつ':
            out.add(('じ' if r[0] == 'ち' else 'ず') + r[1:])
    if not first and r[0] in HAN:
        out.add(HAN[r[0]] + r[1:])
    if not last:
        for v in list(out):
            if len(v) >= 2 and v[-1] in 'つくちき':
                out.add(v[:-1] + 'っ')
    return out

def align(word, reading):
    """Return list of (kanji, type, reading) for each kanji, or None."""
    chars = list(word)
    n = len(chars)
    result = []

    def rec(i, j, prev):
        if i == n:
            return j == len(reading)
        c = chars[i]
        if is_kana(c):
            if j < len(reading) and hira(c) == reading[j]:
                return rec(i + 1, j + 1, None)
            return False
        if c == 'ー':
            if j < len(reading):
                return rec(i + 1, j + 1, None)
            return False
        if c == '々':
            if prev is None:
                return False
            c_eff = prev
        else:
            c_eff = c
        k = kd.get(c_eff)
        if not k:
            return False
        cands = []
        for r in on_list(k):
            for v in variants(r, i == 0, i == n - 1):
                cands.append(('on', r, v))
        for r in kun_stems(k):
            for v in variants(r, i == 0, i == n - 1):
                cands.append(('kun', r, v))
        cands.sort(key=lambda x: -len(x[2]))
        for t, base, v in cands:
            if reading.startswith(v, j):
                result.append((c_eff, t, base))
                if rec(i + 1, j + len(v), c_eff):
                    return True
                result.pop()
        return False

    if rec(0, 0, None):
        return list(result)
    return None

examples = collections.defaultdict(lambda: {'on': [], 'kun': []})
aligned = 0
for v in vocab:
    w = v['w'].replace('～', '')
    if not w or not any(is_kanji(c) for c in w):
        continue
    if not all(is_kana(c) or is_kanji(c) or c == 'ー' for c in w):
        continue
    al = align(w, v['r'])
    if not al:
        continue
    aligned += 1
    for kch, t, base in al:
        examples[kch][t].append({'w': w, 'r': v['r'], 'm': v['m'], 'l': v['l'], 'base': base,
                                 'nk': sum(is_kanji(c) for c in w)})

def pick(lst, prefer_compound, kanji_level):
    # prefer easy words (higher l number), then nice shape, cover distinct readings first
    def score(e):
        shape = (0 if (e['nk'] == 2) else 1) if prefer_compound else (0 if e['nk'] == 1 else 1)
        harder = 0 if e['l'] >= kanji_level else 1
        return (harder, -e['l'], shape, len(e['w']))
    lst = sorted(lst, key=score)
    chosen, bases = [], set()
    for e in lst:
        if e['base'] not in bases and len(chosen) < 3:
            chosen.append(e); bases.add(e['base'])
    for e in lst:
        if len(chosen) >= 3:
            break
        if e not in chosen and all(e['w'] != c['w'] for c in chosen):
            chosen.append(e)
    return [{'w': e['w'], 'r': e['r'], 'm': short(e['m'], 60), 'l': e['l']} for e in chosen]

def short(m, n):
    return m if len(m) <= n else m[:n].rsplit(',', 1)[0].rstrip() if ',' in m[:n] else m[:n - 1] + '…'

def katakana(s):
    return ''.join(chr(ord(c) + 0x60) if 'ぁ' <= c <= 'ゖ' else c for c in s)

kanji_out = []
for ch, k in kd.items():
    lvl = k.get('jlpt_new')
    if not lvl:
        continue
    meanings = [m for m in (k.get('wk_meanings') or k.get('meanings') or []) if 'radical' not in m.lower()]
    if not meanings:
        meanings = [m for m in (k.get('meanings') or []) if 'radical' not in m.lower()]
    if not meanings:
        continue
    seen_m, clean_m = set(), []
    for m in meanings:
        m = m.lstrip('^!').strip()
        if m and m.lower() not in seen_m:
            seen_m.add(m.lower()); clean_m.append(m)
    meanings = clean_m
    ex = examples.get(ch, {'on': [], 'kun': []})
    kanji_out.append({
        'k': ch,
        'l': lvl,
        's': k.get('strokes') or 0,
        'm': meanings[:4],
        'on': [katakana(r) for r in (k.get('readings_on') or [])],
        'kun': list(k.get('readings_kun') or []),
        'eo': pick(ex['on'], True, lvl),
        'ek': pick(ex['kun'], False, lvl),
        'f': k.get('freq') or 9999,
    })
kanji_out.sort(key=lambda x: (-x['l'], x['f']))
for x in kanji_out:
    del x['f']

json.dump(vocab, open(os.path.join(out, 'vocab.json'), 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
json.dump(kanji_out, open(os.path.join(out, 'kanji.json'), 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))

print('vocab', len(vocab), collections.Counter(v['l'] for v in vocab))
print('kanji', len(kanji_out), collections.Counter(v['l'] for v in kanji_out))
print('aligned words', aligned)
print('kanji with on-examples', sum(1 for x in kanji_out if x['eo']), 'kun-examples', sum(1 for x in kanji_out if x['ek']))
print('kanji w/ kun readings but no kun ex', sum(1 for x in kanji_out if x['kun'] and not x['ek']))
for ch in '日人食行生学話高':
    x = next(y for y in kanji_out if y['k'] == ch)
    print(ch, x['on'], x['kun'][:4], [e['w'] + '(' + e['r'] + ')' for e in x['eo']], [e['w'] + '(' + e['r'] + ')' for e in x['ek']])
