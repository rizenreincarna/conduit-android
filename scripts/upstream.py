#!/usr/bin/env python3
"""Deterministic upstream catalog import. Run: python3 scripts/upstream.py strings [--check]."""
import argparse
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
UPSTREAM = ROOT / '.upstream/hermes-conduit'
FORMAT = re.compile(r'%(?P<position>\d+\$)?(?P<flags>[-+ #0,(]*)(?P<width>\d*)(?P<precision>\.\d+)?(?P<length>ll|l)?(?P<type>[@diufFeEgGs%])')


def android_format(value):
    def convert(match):
        kind = {'@': 's', 'i': 'd', 'u': 'd'}.get(match['type'], match['type'])
        return '%' + (match['position'] or '') + match['flags'] + match['width'] + (match['precision'] or '') + kind
    return FORMAT.sub(convert, value)


def unit_value(localization):
    if 'stringUnit' in localization:
        return android_format(localization['stringUnit']['value'])
    variations = localization.get('variations', {})
    if set(variations) == {'plural'}:
        return {'plural': {category: unit_value(value) for category, value in variations['plural'].items()}}
    raise ValueError(f'Unsupported catalog structure: {localization}')


def string_outputs(upstream=UPSTREAM):
    catalogs = {'Localizable': upstream / 'Conduit/Localizable.xcstrings',
                'InfoPlist': upstream / 'Conduit/InfoPlist.xcstrings'}
    by_language = {}
    source = None
    for table, path in catalogs.items():
        if not path.exists():
            if table == 'Localizable':
                raise FileNotFoundError(path)
            continue
        catalog = json.loads(path.read_text())
        source = source or catalog['sourceLanguage']
        for key, entry in catalog['strings'].items():
            for lang, localization in entry.get('localizations', {}).items():
                by_language.setdefault(lang, {}).setdefault(table, {})[key] = unit_value(localization)
            # Source keys remain the fallback even for entries without extracted English units.
            by_language.setdefault(source, {}).setdefault(table, {}).setdefault(key, android_format(key))
    languages = [source] + sorted(lang for lang in by_language if lang != source)
    outputs = {}
    for lang in languages:
        outputs[f'app/src/main/assets/l10n/{lang}.json'] = json.dumps(by_language[lang], ensure_ascii=False, sort_keys=True, indent=2) + '\n'
    outputs['app/src/main/assets/l10n/index.json'] = json.dumps({'source': source, 'shipped': languages}, indent=2) + '\n'
    config = ET.Element('locale-config', {'xmlns:android': 'http://schemas.android.com/apk/res/android'})
    for lang in languages:
        ET.SubElement(config, 'locale', {'android:name': lang})
    ET.indent(config)
    outputs['app/src/main/res/xml/locales_config.xml'] = '<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(config, encoding='unicode') + '\n'
    return outputs


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['strings'])
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    outputs = string_outputs()
    stale = []
    for name, content in outputs.items():
        path = ROOT / name
        if args.check:
            if not path.exists() or path.read_text() != content:
                stale.append(name)
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)
    if stale:
        parser.exit(1, 'Stale catalog outputs:\n' + '\n'.join(stale) + '\n')
    print(f'{"Verified" if args.check else "Generated"} {len(outputs)} localization files')


if __name__ == '__main__':
    main()
