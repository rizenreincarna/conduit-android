#!/usr/bin/env python3
"""Where an upstream Swift file or name goes in the Android port.

    scripts/portmap.py path <upstream path>       Kotlin path (docs/PORTING.md §1, §11)
    scripts/portmap.py classify <upstream path>   core | partial | later:<sub> | skip | outside
    scripts/portmap.py sections                   AppState sections: lines, title, Kotlin file
    scripts/portmap.py owner <SwiftName>          where the name is declared and who owns it

Rules live in upstream/port-map.json; batch ownership in the plan's batches.json.
"""
import fnmatch
import json
import os
import re
import subprocess
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UPSTREAM = os.path.join(REPO, '.upstream', 'hermes-conduit')
MAP = json.load(open(os.path.join(REPO, 'upstream', 'port-map.json'), encoding='utf-8'))
BATCHES = os.path.join(REPO, 'docs', 'superpowers', 'plans', '2026-11-01-conduit-android-core.batches.json')
MAIN = 'app/src/main/kotlin/app/conduit'
TEST = 'app/src/test/kotlin/app/conduit'
APPSTATE = 'Conduit/Services/AppState.swift'
SECTION_DIR = MAIN + '/services/appstate'


def _match(path, patterns):
    return any(path.startswith(p[:-1]) if p.endswith('/*') else fnmatch.fnmatchcase(path, p)
               for p in patterns)


def classify(path):
    if _match(path, MAP['skip']):
        return 'skip'
    if path in MAP['core']:
        return 'core'
    if path in MAP['partial']:
        return 'partial'
    for sub, patterns in MAP['later'].items():
        if _match(path, patterns):
            return 'later:' + sub
    if path.startswith(tuple(MAP['core_roots'])):
        return 'core'
    return 'outside'


def kotlin_path(path):
    """Kotlin file for an upstream Swift file; None for targets outside the app."""
    if path in MAP['paths']:
        return MAP['paths'][path]
    parts = path[:-len('.swift')].split('/')
    root, dirs, name = parts[0], parts[1:-1], parts[-1].replace('+', '')
    if root == 'ConduitTests':
        return f'{TEST}/{name}.kt'
    prefix = {'Conduit': [], 'Shared': ['shared']}.get(root)
    if prefix is None:
        return None
    return '/'.join([MAIN] + prefix + [d.lower() for d in dirs] + [name + '.kt'])


def pascal_title(mark):
    """`// MARK: - Session compression (`x`)` or its title → `SessionCompression`."""
    t = re.sub(r'^\s*//\s*MARK:\s*', '', mark).strip()
    if t.startswith('- '):
        t = t[2:]
    t = re.split(r' \(|:', t, maxsplit=1)[0].replace("'", '').replace('`', '')
    return ''.join(w[:1].upper() + w[1:] for w in re.split(r'[^A-Za-z0-9]+', t) if w)


def section_files(titles):
    """Kotlin section file per MARK title, in order; a repeated title gets `2`."""
    seen, out = set(), []
    for title in titles:
        if title in MAP['sections']:
            out.append(MAP['sections'][title])
            continue
        name = pascal_title(title)
        while name in seen:
            name = re.sub(r'(\d*)$', lambda m: str(int(m.group(1) or 1) + 1), name, count=1)
        seen.add(name)
        out.append(f'{SECTION_DIR}/AppState_{name}.kt')
    return out


def appstate_sections(upstream=UPSTREAM):
    """[(first line, last line, title, Kotlin file)] for AppState.swift, starting with
    the support code before the class and the class declaration itself."""
    src = os.path.join(upstream, APPSTATE)
    decls = subprocess.run([sys.executable, os.path.join(REPO, 'scripts', 'swift-decls.py'), src],
                           capture_output=True, text=True, check=True).stdout.splitlines()
    total = sum(1 for _ in open(src, encoding='utf-8'))
    top = [(int(m.group(1)), m.group(2)) for m in (re.match(r'^\s*(\d+)  (\S.*)$', l) for l in decls) if m]
    member = [(int(m.group(1)), m.group(2)) for m in (re.match(r'^\s*(\d+) {6}(\S.*)$', l) for l in decls) if m]
    class_start = next(n for n, s in top if re.search(r'\bclass AppState\b', s))
    class_end = next((n for n, s in top if n > class_start), total + 1) - 1
    marks = [(n, s) for n, s in member if class_start < n <= class_end and s.startswith('// MARK')]
    marks += [(n, s) for n, s in top if n > class_end and s.startswith('// MARK')]
    titles = [re.sub(r'^//\s*MARK:\s*(- )?', '', s).strip() for _, s in marks]
    files = section_files(titles)
    starts = [n for n, _ in marks]
    ends = [s - 1 for s in starts[1:]] + [total]
    ends = [class_end if s <= class_end < e else e for s, e in zip(starts, ends)]
    out = [(1, class_start - 1, 'Support', f'{SECTION_DIR}/AppState_Support.kt'),
           (class_start, starts[0] - 1, 'class AppState', f'{MAIN}/services/AppState.kt')]
    return out + list(zip(starts, ends, titles, files))


def _batches():
    if not os.path.exists(BATCHES):
        return {}
    return json.load(open(BATCHES, encoding='utf-8'))['batches']


def _owner_of(path, line, batches):
    kind = classify(path)
    if kind == 'partial':
        spec = MAP['partial'][path]
        kind = 'core' if any(lo <= line <= hi for lo, hi in spec['lines']) else 'later:' + spec['rest']
    if kind != 'core':
        return kind + ('  (apply docs/PORTING.md §13)' if kind.startswith('later') else '')
    if path == APPSTATE:
        for lo, hi, title, kt in appstate_sections():
            if lo <= line <= hi:
                names = [b for b, v in batches.items() if title in v.get('sections', [])]
                return f'core  section "{title}" → {kt}  batches {names}  (later-owned members: §13)'
    names = [b for b, v in batches.items() if path in v.get('files', [])]
    return f'core  {kotlin_path(path)}  batches {names}'


def owner(name):
    decl = re.compile(r'^\s*(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|private|fileprivate|internal|final|static|'
                      r'class|override|nonisolated|mutating|lazy|weak|indirect|private\(set\))\s+)*'
                      r'(struct|class|enum|protocol|actor|typealias|func|var|let|case|extension)\s+'
                      + re.escape(name) + r'\b')
    batches = _batches()
    hits = []
    for root in ('Conduit', 'Shared'):
        for d, _, files in os.walk(os.path.join(UPSTREAM, root)):
            for f in sorted(files):
                if not f.endswith('.swift'):
                    continue
                full = os.path.join(d, f)
                rel = os.path.relpath(full, UPSTREAM)
                for no, text in enumerate(open(full, encoding='utf-8'), start=1):
                    if decl.match(text):
                        hits.append(f'{rel}:{no}  {text.strip()[:80]}\n    {_owner_of(rel, no, batches)}')
    print('\n'.join(hits) if hits else f'{name}: no declaration in Conduit/ or Shared/')
    if name in MAP['skip_decls']:
        print(f'skip_decls: {MAP["skip_decls"][name]}')


def main():
    args = sys.argv[1:]
    if len(args) == 2 and args[0] == 'path':
        print(kotlin_path(args[1]))
    elif len(args) == 2 and args[0] == 'classify':
        print(classify(args[1]))
    elif args == ['sections']:
        for lo, hi, title, kt in appstate_sections():
            print(f'{lo:6} {hi:6}  {title}  →  {kt}')
    elif len(args) == 2 and args[0] == 'owner':
        owner(args[1])
    else:
        sys.exit(__doc__)


if __name__ == '__main__':
    main()
