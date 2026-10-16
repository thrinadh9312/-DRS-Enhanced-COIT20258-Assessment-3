"""Convert DRS-Initial *_Report.txt exports into the enhanced server import format.
Usage: python scripts/convert_initial_exports.py EXPORT_FOLDER data/initial-import.json
No database is accessed by this conversion tool.
"""
import json
import sys
from pathlib import Path

def convert(path):
    content = path.read_text(encoding='utf-8-sig')
    before, separator, description = content.partition('Description:\n')
    if not separator:
        raise ValueError(f'{path.name}: Description section is missing')
    fields = dict(line.split(': ', 1) for line in before.splitlines() if ': ' in line)
    mapping = {'Incident ID':'legacy_id', 'Disaster Type':'type', 'Location':'location',
               'Severity':'severity', 'Priority':'priority', 'Status':'status',
               'Primary Department':'primary_department', 'Supporting Department':'supporting_department',
               'Allocated Resource':'resource'}
    record = {target: fields.get(source, '').strip() for source, target in mapping.items()}
    record['population'] = int(fields['Affected Population'])
    record['occurred_at'] = fields['Date'].strip() + 'T' + fields['Time'].strip()
    record['description'] = description.strip()
    if not record['legacy_id']:
        raise ValueError(f'{path.name}: Incident ID is missing')
    return record

if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    inputs = sorted(Path(sys.argv[1]).glob('*.txt'))
    if not inputs:
        raise SystemExit('No .txt exports were found')
    records = [convert(path) for path in inputs]
    Path(sys.argv[2]).write_text(json.dumps({'incidents': records}, indent=2), encoding='utf-8')
    print(f'Converted {len(records)} records. Import the JSON using the client Import initial data tab.')
