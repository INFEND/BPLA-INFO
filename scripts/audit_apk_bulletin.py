#!/usr/bin/env python3
"""Verify the new PDF-derived factual catalog really resides inside each APK."""
import argparse
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
FILES = ['catalog_lostarmour_2024_verified.json', 'bulletin30_2025.json']

def audit_apk(apk, assets=ASSETS):
    apk=Path(apk)
    with zipfile.ZipFile(apk, 'r') as archive:
        names=set(archive.namelist())
        for name in FILES:
            path='assets/' + name
            if path not in names:
                raise ValueError('Missing APK asset: ' + path)
            expected=(Path(assets) / name).read_bytes()
            actual=archive.read(path)
            if expected != actual:
                raise ValueError('Different or truncated APK asset: ' + path)
            root=json.loads(actual)
            if name=='bulletin30_2025.json':
                assert root['schema']==1
                n=len(root['entries'])
                facts=sum(len(e['specRows']) for e in root['entries'])
                if n !=374 or facts!=3516:
                    raise ValueError('Unexpected supplemental catalog cardinality')
            else:
                if len(root['entries'])!=87:
                    raise ValueError('Old catalog was replaced or truncated')
    return {'base_models':87,'supplemental_source_models':374,'supplemental_spec_rows':3516}

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('apk')
    args=parser.parse_args()
    info=audit_apk(args.apk)
    print('PASS:',args.apk,info,'both catalogs physically embedded unchanged')

if __name__=='__main__':main()
