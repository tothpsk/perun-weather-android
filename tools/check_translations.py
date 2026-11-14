#!/usr/bin/env python3
"""Checks that every values-XX resource set matches the default one: same keys, same format placeholders, plural forms."""
import re
from pathlib import Path
import xml.etree.ElementTree as ET

PH=re.compile(r'%(?:\d+\$)?[sd]|%%')

def load(d):
    strings={};plurals={}
    for p in sorted(d.glob('*.xml')):
        for el in ET.parse(p).getroot():
            if el.tag=='string': strings[el.get('name')]=(el.get('translatable')!='false',el.text or '')
            elif el.tag=='plurals': plurals[el.get('name')]={i.get('quantity'):(i.text or '') for i in el}
    return strings,plurals

def main():
    res=Path(__file__).resolve().parents[1]/'app/src/main/res'
    base,basepl=load(res/'values')
    need={k for k,(t,_) in base.items() if t}
    for d in sorted(res.glob('values-??')):
        s,pl=load(d)
        assert not (need-s.keys()), d.name+': missing '+str(sorted(need-s.keys()))[:300]
        assert not (s.keys()-need), d.name+': extra '+str(sorted(s.keys()-need))[:300]
        for k in sorted(need):
            a=sorted(PH.findall(base[k][1]));b=sorted(PH.findall(s[k][1]))
            assert a==b, d.name+': placeholders differ in '+k+' '+str(a)+' '+str(b)
        for k,v in basepl.items():
            assert k in pl and {'one','other'}<=pl[k].keys(), d.name+': plural '+k
            for q in ('one','other'):
                assert sorted(PH.findall(v[q]))==sorted(PH.findall(pl[k][q])), d.name+': plural placeholders '+k+' '+q
        print('PASS: '+d.name+' has all '+str(len(need))+' strings and '+str(len(basepl))+' plural groups with matching placeholders')

if __name__=='__main__':
    main()
