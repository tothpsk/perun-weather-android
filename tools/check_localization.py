#!/usr/bin/env python3
"""Static localization checks, not an Android compiler or an exhaustive language audit."""
from pathlib import Path
import json
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET

def literals(s):
 out=[];n=len(s)
 def comment(i):
  if s.startswith('//',i):
   e=s.find('\n',i+2);return n if e<0 else e
  if s.startswith('/*',i):
   d=1;i+=2
   while i<n and d:
    if s.startswith('/*',i):d+=1;i+=2
    elif s.startswith('*/',i):d-=1;i+=2
    else:i+=1
   return i
 def char(i):
  i+=1
  while i<n:
   if s[i]=='\\':i+=2
   elif s[i]=="'":return i+1
   else:i+=1
  return i
 def expr(i):
  d=1
  while i<n:
   c=comment(i)
   if c is not None:i=c;continue
   if s[i]=='"':i=string(i)
   elif s[i]=="'":i=char(i)
   elif s[i]=='{':d+=1;i+=1
   elif s[i]=='}':
    d-=1;i+=1
    if not d:return i
   else:i+=1
  raise ValueError('unclosed interpolation')
 def string(i):
  a=i;raw=s.startswith('"""',i);q='"""' if raw else '"';i+=len(q);segments=[];last=i
  while i<n:
   if s.startswith(q,i):
    segments.append(('text',last,i));end=i+len(q)
    out.append({'start':a,'end':end,'raw':raw,'segments':segments,'body':s[a+len(q):i]});return end
   if not raw and s[i]=='\\':i+=2;continue
   if s.startswith('${',i):
    segments.append(('text',last,i));st=i+2;i=expr(st);segments.append(('expr',st,i-1));last=i;continue
   if s[i]=='$' and i+1<n and (s[i+1].isalpha() or s[i+1]=='_'):
    segments.append(('text',last,i));st=i+1;i+=2
    while i<n and (s[i].isalnum() or s[i]=='_'):i+=1
    segments.append(('expr',st,i));last=i;continue
   i+=1
  raise ValueError('unclosed string')
 i=0
 while i<n:
  c=comment(i)
  if c is not None:i=c;continue
  if s[i]=='"':i=string(i)
  elif s[i]=="'":i=char(i)
  else:i+=1
 return sorted(out,key=lambda x:x['start'])


def decode_android(s):
    if s.startswith('"') and s.endswith('"'):
        s = s[1:-1]
    return re.sub(r'\\(.)', lambda m: {'n':'\n','r':'\r','t':'\t'}.get(m[1],m[1]), s)

def check(root):
    strings = {}
    plural_names = set()
    for path in sorted((root/'app/src/main/res/values').glob('*.xml')):
        for el in ET.parse(path).getroot():
            name = el.get('name')
            if el.tag == 'string':
                assert name not in strings, f'Duplicate string: {name}'
                strings[name] = decode_android(el.text or '')
                if el.get('formatted') != 'false':
                    rest = re.sub(r'%(?:[0-9]+\$[sd]|%)','',strings[name])
                    assert '%' not in rest, f'Unsupported formatter: {name}'
            elif el.tag == 'plurals':
                assert name not in plural_names, f'Duplicate plurals: {name}'
                plural_names.add(name)
                assert {'one','few','other'} <= {i.get('quantity') for i in el}, name
    accents = re.compile('[áäčďéíĺľňóôŕšťúýžÁÄČĎÉÍĹĽŇÓÔŔŠŤÚÝŽ]')
    technical_terms = {'búrk','dážď','zrážk','poľadov','ľad','mráz','horúč'}
    count = 0
    for path in sorted((root/'app/src/main').rglob('*.kt')):
        source = path.read_text(encoding='utf-8')
        for name in re.findall(r'R\.string\.([a-zA-Z0-9_]+)',source):
            assert name in strings, f'{path.name}: unknown string {name}'
        for name in re.findall(r'R\.plurals\.([a-zA-Z0-9_]+)',source):
            assert name in plural_names, f'{path.name}: unknown plurals {name}'
        for token in literals(source):
            value = ''.join(source[a:b] for kind,a,b in token['segments'] if kind=='text')
            if path.name=='LocationModelCatalogue.kt':
                value=value.replace('Météo-France','').replace('ČHMÚ','')
            if path.name=='OverviewTileKit.kt' and value in technical_terms:
                continue
            assert not accents.search(value), f'{path.name}: accented literal {value[:80]!r}'
        count+=1
    html=(root/'app/src/main/assets/map/librewxr_map.html').read_text(encoding='utf-8')
    assert not accents.search(html), 'Hardcoded accented text in map asset'
    catalogue=(root/'app/src/main/java/sk/perun/weather/ui/MapStrings.kt').read_text(encoding='utf-8')
    mapping=dict(re.findall(r'"([a-z0-9_]+)" to R\.string\.([a-z0-9_]+)',catalogue))
    keys=set(re.findall(r'\{\{pw:([a-z0-9_]+)\}\}',html))|set(re.findall(r"tr\('([a-z0-9_]+)'",html))
    assert keys<=mapping.keys(), f'Missing map entries: {keys-mapping.keys()}'
    assert set(mapping.values())<=strings.keys(), 'Missing map resources'
    scripts=re.findall(r'<script>([\s\S]*?)</script>',html)
    assert len(scripts)==1 and html.count('__PERUN_I18N_JSON__')==1
    data=json.dumps({k:strings[n] for k,n in mapping.items()},ensure_ascii=False).replace('<',r'\u003c').replace('\u2028',r'\u2028').replace('\u2029',r'\u2029')
    script=scripts[0].replace('__PERUN_I18N_JSON__',data)
    node=shutil.which('node')
    if node:
        subprocess.run([node,'--check'],input=script,text=True,check=True)
        print('PASS: map JavaScript syntax')
    else:
        print('SKIP: map JavaScript syntax (Node.js unavailable)')
    print(f'PASS: {len(strings)} strings, {len(plural_names)} plural groups, {count} Kotlin files, {len(mapping)} map entries')
    print('Scope: XML/formats, resource references, accented-text guard, map keys. Does not prove compilation or full linguistic correctness.')

if __name__=='__main__':
    check(Path(__file__).resolve().parents[1])
