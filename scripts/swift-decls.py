#!/usr/bin/env python3
"""Print a Swift file's declarations with line numbers, bodies elided.

    scripts/swift-decls.py <file.swift> [--from N] [--to M]

Prints, indented by nesting: type declarations; members at type level (stored
and computed properties, funcs, inits, enum cases, typealiases, subscripts);
attribute lines, `#if`/`#else`/`#endif` and `// MARK:` lines at type level.
Function and computed-property bodies are skipped, so a 38K-line file reads as
its outline. Used by the skeleton phase (docs/PORTING.md §10).
"""
import re
import sys

DECL = re.compile(
    r'^(?:@[\w.]+(?:\([^)]*\))?\s+)*'
    r'(?:(?:public|private|fileprivate|internal|open|final|static|class|override|'
    r'nonisolated|mutating|nonmutating|lazy|weak|unowned|convenience|required|'
    r'indirect|dynamic|optional|(?:private|fileprivate|internal|public)\(set\)|'
    r'nonisolated\(unsafe\))\s+)*'
    r'(struct|class|enum|protocol|extension|actor|func|init[?!]?|deinit|var|let|'
    r'case|typealias|subscript|associatedtype)\b')
TYPES = {'struct', 'class', 'enum', 'protocol', 'extension', 'actor'}
QUOTE = re.compile(r'(#*)("""|")')


def code_braces(src):
    """Per source line, the '{'/'}' characters that are code (not inside
    strings or comments), in order."""
    per_line = [[]]
    stack = ['code']  # 'code' | 'block' | ('str', hashes, multiline) | ('interp', parens)
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        mode = stack[-1]
        if c == '\n':
            per_line.append([])
            if isinstance(mode, tuple) and mode[0] == 'str' and not mode[2]:
                stack.pop()  # unterminated single-line string: recover
            i += 1
            continue
        if mode == 'block':
            if src.startswith('*/', i):
                stack.pop(); i += 2
            elif src.startswith('/*', i):
                stack.append('block'); i += 2
            else:
                i += 1
            continue
        if isinstance(mode, tuple) and mode[0] == 'str':
            _, hashes, multi = mode
            if c == '\\' and src.startswith('#' * hashes, i + 1):
                j = i + 1 + hashes
                if j < n and src[j] == '(':
                    stack.append(('interp', 0)); i = j + 1
                elif j < n and src[j] == '\n':
                    i = j  # line continuation: the newline still counts
                else:
                    i = j + 1
                continue
            close = ('"""' if multi else '"') + '#' * hashes
            if src.startswith(close, i):
                stack.pop(); i += len(close)
            else:
                i += 1
            continue
        # code, or code inside a string interpolation
        if src.startswith('//', i):
            j = src.find('\n', i)
            i = n if j < 0 else j
            continue
        if src.startswith('/*', i):
            stack.append('block'); i += 2
            continue
        if c in '"#':
            m = QUOTE.match(src, i)
            if m:
                stack.append(('str', len(m.group(1)), m.group(2) == '"""'))
                i = m.end()
                continue
        if isinstance(mode, tuple) and mode[0] == 'interp':
            if c == '(':
                stack[-1] = ('interp', mode[1] + 1)
            elif c == ')':
                if mode[1] == 0:
                    stack.pop(); i += 1
                    continue
                stack[-1] = ('interp', mode[1] - 1)
        if c in '{}':
            per_line[-1].append(c)
        i += 1
    return per_line


def main():
    args = sys.argv[1:]
    if not args or args[0].startswith('-'):
        sys.exit(__doc__)
    lo = int(args[args.index('--from') + 1]) if '--from' in args else 1
    hi = int(args[args.index('--to') + 1]) if '--to' in args else 10**9
    src = open(args[0], encoding='utf-8').read()
    lines = src.split('\n')
    braces = code_braces(src)

    depth = 0
    type_depths = [0]   # brace depths whose contents are type members
    pending_type = False
    skip_to = None      # inside a body: depth at which it closes
    for no, (text, bs) in enumerate(zip(lines, braces), start=1):
        s = text.strip()
        at_type_level = skip_to is None and depth == type_depths[-1]
        if at_type_level and s:
            m = DECL.match(s)
            shown = m or s.startswith(('// MARK', '#if', '#else', '#elseif', '#endif')) \
                or (s.startswith('@') and not bs)
            if shown and lo <= no <= hi:
                print(f'{no:6}  {"    " * (len(type_depths) - 1)}{s}')
            if m and m.group(1) in TYPES:
                pending_type = True
        for b in bs:
            if b == '{':
                depth += 1
                if skip_to is None and pending_type:
                    type_depths.append(depth)
                    pending_type = False
                elif skip_to is None:
                    skip_to = depth - 1
            else:
                depth -= 1
                if skip_to is not None:
                    if depth == skip_to:
                        skip_to = None
                elif len(type_depths) > 1 and depth < type_depths[-1]:
                    type_depths.pop()
    if depth != 0:
        print(f'warning: unbalanced braces (final depth {depth})', file=sys.stderr)


if __name__ == '__main__':
    main()
