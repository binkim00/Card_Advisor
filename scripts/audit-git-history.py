#!/usr/bin/env python3
"""Audit all file versions reachable from HEAD before pushing main. Values stay private."""
from pathlib import Path
import argparse, importlib.util, json, subprocess, tempfile, sys

ROOT=Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location('secret_rules',ROOT/'scripts/check-staged-secrets.py')
rules=importlib.util.module_from_spec(spec);spec.loader.exec_module(rules)

def audit(repo):
    git=lambda *args:rules.git(repo,*args)
    objects={}
    for line in git('rev-list','--objects','HEAD').splitlines():
        parts=line.split(b' ',1)
        if len(parts)==2:objects[parts[0]]=parts[1].decode('utf-8')
    batch=subprocess.run(['git','-C',str(repo),'cat-file','--batch-check'],input=b''.join(oid+b'\n' for oid in objects),capture_output=True,check=True)
    blobs={row.split()[0]:int(row.split()[2]) for row in batch.stdout.splitlines() if row.split()[1]==b'blob'}
    secrets=rules.local_values(repo)
    findings=[]
    for oid,size in blobs.items():
        name=objects[oid]
        def finding(label,line=0):findings.append({'path':name,'line':line,'rule':label,'blob':oid.decode()})
        if rules.forbidden(name):finding('forbidden-private-path');continue
        if size>2*1024*1024:finding('large-file-review-required');continue
        data=git('cat-file','blob',oid.decode())
        binary=b'\x00' in data or name.lower().endswith(('.png','.jpg','.jpeg','.webp','.gif','.jar'))
        if binary:
            asset=name.startswith('android/app/src/main/res/') and name.lower().endswith(('.png','.jpg','.jpeg','.webp','.gif'))
            if not (asset or name.endswith('/gradle/wrapper/gradle-wrapper.jar')):finding('binary-review-required')
            continue
        try:data.decode('utf-8-sig')
        except UnicodeDecodeError:finding('encoding-review-required');continue
        for label,pattern in rules.RULES:
            for match in pattern.finditer(data):finding(label,data[:match.start()].count(b'\n')+1)
        for secret in secrets:
            at=data.find(secret)
            if at>=0:finding('matches-private-local-setting',data[:at].count(b'\n')+1)
    return {'checkedHead':git('rev-parse','HEAD').decode().strip(),'commits':int(git('rev-list','--count','HEAD')),
        'trackedFiles':len(git('ls-files','-z').split(b'\0'))-1,'uniqueBlobs':len(blobs),'findings':findings}

def self_test():
    with tempfile.TemporaryDirectory(prefix='advisor-history-test-') as folder:
        repo=Path(folder)
        def git(*args):return rules.git(repo,*args)
        git('init','-q');git('config','user.name','Synthetic Test');git('config','user.email','test@example.test')
        p=repo/'README.md';p.write_text('sk-proj-'+'A'*48);git('add','README.md');git('commit','-qm','synthetic secret')
        p.write_text('safe now');git('add','README.md');git('commit','-qm','remove synthetic secret')
        result=audit(repo)
        assert result['commits']==2 and any(x['rule']=='openai-key' for x in result['findings'])
    print('History audit self-test passed: secret removed from current files is still blocked in history.')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--self-test',action='store_true');args=parser.parse_args()
    if args.self_test:self_test();return 0
    result=audit(ROOT)
    folder=ROOT/'.run';folder.mkdir(exist_ok=True)
    (folder/'history-audit.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result))
    return 1 if result['findings'] else 0

if __name__=='__main__':
    try:sys.exit(main())
    except Exception as error:
        print('History audit could not complete ('+type(error).__name__+'); values withheld.',file=sys.stderr);sys.exit(2)
