"""Deploy an already-built dist-mall to the mall site's root, with rollback.

Build first: pnpm --dir yudao-ui-admin-vue3 build:mall
This switches only the admin static release and the shared mall Nginx locations.
"""
import argparse
import base64
import hashlib
import json
import pathlib
import re
import subprocess
import sys
import tarfile
import tempfile
import uuid


REMOTE = r'''
import base64,datetime,fcntl,hashlib,json,os,pathlib,re,shutil,socket,subprocess,tarfile,tempfile,time,urllib.request,urllib.error
payload=json.loads(base64.b64decode(PAYLOAD))
if socket.gethostname()!='VM-0-5-ubuntu':raise RuntimeError('Unexpected deployment host')
lock=open('/run/lock/yuanmall-admin-root.lock','w')
fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
archive=pathlib.Path(payload['archive'])
if hashlib.sha256(archive.read_bytes()).hexdigest()!=payload['sha256']:raise RuntimeError('Archive checksum mismatch')
current=pathlib.Path('/production/yuanmall/web/current')
if not current.is_symlink():raise RuntimeError('Existing release pointer is not a symlink')
previous=current.resolve(strict=True)
releases=current.parent/'releases'
if previous.parent!=releases or not (previous/'admin/index.html').is_file():raise RuntimeError('Unexpected previous release')
snippet=pathlib.Path('/etc/nginx/snippets/yuanmall-locations.conf')
original=snippet.read_bytes()
old_stat=snippet.stat()
config=base64.b64decode(payload['nginx'])
for required in (b'/admin-api/',b'/app-api/',b'/infra/ws',b'yuanmall-assets-locations.conf'):
    if required not in original or required not in config:raise RuntimeError('Missing existing route: '+required.decode())
stamp=datetime.datetime.now().astimezone().strftime('%Y%m%dT%H%M%S%z')
backup=pathlib.Path('/production/yuanmall/backups')/('admin-root-'+stamp)
backup.mkdir(mode=0o700)
shutil.copy2(snippet,backup/snippet.name)
(backup/'previous-release.txt').write_text(str(previous)+'\n')
release=releases/(stamp+'-root')
release.mkdir(mode=0o755)
before_sites={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in pathlib.Path('/etc/nginx/sites-enabled').iterdir() if p.is_file()}
override=pathlib.Path('/workspaces/yuanmall/yudao-ui-admin-vue3/.env.prod.local')
old_override=override.read_bytes() if override.is_file() else None
override_stat=override.stat() if old_override is not None else None
if old_override is not None:shutil.copy2(override,backup/'env.prod.local')

def atomic_write(path,data,mode=0o644):
    with tempfile.NamedTemporaryFile(dir=path.parent,delete=False) as handle:
        handle.write(data);name=handle.name
    os.chmod(name,mode);os.replace(name,path)

def point_at(target):
    temp=current.with_name('.current-admin-root')
    if temp.exists() or temp.is_symlink():raise RuntimeError('Temporary release pointer already exists')
    temp.symlink_to(target);os.replace(temp,current)

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,req,fp,code,msg,headers,newurl):return None

def check_page(path):
    request=urllib.request.Request('http://127.0.0.1:18082'+path,headers={'Host':'mall.nexrur.ai','tenant-id':'1','terminal':'10'})
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}),NoRedirect())
    try:response=opener.open(request,timeout=8)
    except urllib.error.HTTPError as error:response=error
    with response:
        return response.status,response.headers.get('Content-Type',''),response.read()

changed=False
try:
    with tarfile.open(archive,'r:gz') as bundle:
        for entry in bundle.getmembers():
            path=pathlib.PurePosixPath(entry.name)
            if not entry.isfile() or path.is_absolute() or '..' in path.parts or path.parts[0]!='admin':raise RuntimeError('Unsafe archive entry')
        bundle.extractall(release,filter='data')
    for path in release.rglob('*'):
        os.chmod(path,0o755 if path.is_dir() else 0o644)
    html_bytes=(release/'admin/index.html').read_bytes()
    html=html_bytes.decode('utf-8')
    if '/assets/' not in html or '/admin/assets/' in html:raise RuntimeError('Build does not target the site root')
    # Preserve lazy-loaded chunks for users who still have the previous release open.
    old_assets=previous/'admin/assets'
    new_assets=release/'admin/assets'
    for old in old_assets.rglob('*'):
        if not old.is_file():continue
        new=new_assets/old.relative_to(old_assets)
        if not new.exists():
            new.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(old,new)
        elif hashlib.sha256(old.read_bytes()).digest()!=hashlib.sha256(new.read_bytes()).digest():
            raise RuntimeError('Different contents share an existing asset filename')
    if snippet.read_bytes()!=original or current.resolve()!=previous:raise RuntimeError('Deployment changed concurrently')
    if old_override is not None and override.read_bytes()!=old_override:raise RuntimeError('Build override changed concurrently')
    changed=True
    atomic_write(snippet,config)
    subprocess.run(['/usr/sbin/nginx','-t'],check=True,capture_output=True)
    point_at(release)
    if old_override is not None:
        replacement,count=re.subn(rb'(?m)^VITE_BASE_PATH=[^\r\n]*',b'VITE_BASE_PATH=/',old_override)
        if count!=1:raise RuntimeError('Unexpected production base-path override')
        atomic_write(override,replacement,override_stat.st_mode & 0o777)
        os.chown(override,override_stat.st_uid,override_stat.st_gid)
    subprocess.run(['systemctl','reload','nginx'],check=True,capture_output=True)
    for attempt in range(20):
        status,kind,body=check_page('/')
        if status==200 and body==html_bytes:break
        time.sleep(0.25)
    else:raise RuntimeError('Root page verification failed: status='+str(status)+', bytes='+str(len(body)))
    for path in ('/login','/product/spu'):
        status,kind,body=check_page(path)
        if status!=200 or body!=html_bytes:raise RuntimeError('SPA history fallback failed')
    api_checks={}
    for path in ('/app-api/trade/config/get','/admin-api/system/tenant/get-id-by-name?name=yuanmall-readiness'):
        status,kind,body=check_page(path);result=json.loads(body)
        if status!=200 or result.get('code')!=0:raise RuntimeError('API check failed: '+path)
        api_checks[path]={'status':status,'code':result.get('code')}
    if any(hashlib.sha256(pathlib.Path(p).read_bytes()).hexdigest()!=digest for p,digest in before_sites.items()):raise RuntimeError('Unrelated site configuration changed')
    report={'release':str(release),'previous_release':str(previous),'backup_directory':str(backup),'archive_sha256':payload['sha256'],'nginx_test_ok':True,'root_status':200,'history_fallback_ok':True,'api_checks':api_checks,'site_files_unchanged':True,'database_modified':False,'java_restarted':False}
    (backup/'deploy-result.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report))
except Exception:
    if changed:
        atomic_write(snippet,original,old_stat.st_mode & 0o777);os.chown(snippet,old_stat.st_uid,old_stat.st_gid)
        point_at(previous)
        if old_override is not None:
            atomic_write(override,old_override,override_stat.st_mode & 0o777);os.chown(override,override_stat.st_uid,override_stat.st_gid)
        if subprocess.run(['/usr/sbin/nginx','-t'],capture_output=True).returncode==0:subprocess.run(['systemctl','reload','nginx'],capture_output=True)
    raise
finally:
    archive.unlink(missing_ok=True)
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', default='mall')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_.-]+', args.host):
        parser.error('Use an SSH host alias')
    script_dir = pathlib.Path(__file__).resolve().parent
    root = script_dir.parent.parent
    dist = root / 'yudao-ui-admin-vue3/dist-mall'
    html = (dist / 'index.html').read_text(encoding='utf-8')
    if '/assets/' not in html or '/admin/assets/' in html:
        raise RuntimeError('Run build:mall before deployment')
    with tempfile.TemporaryDirectory(prefix='yuanmall-admin-root-') as temp:
        archive = pathlib.Path(temp) / 'admin.tar.gz'
        with tarfile.open(archive, 'w:gz') as bundle:
            for path in sorted(dist.rglob('*')):
                if path.is_symlink():
                    raise RuntimeError('Symlinks are not expected in the static build')
                if path.is_file():
                    bundle.add(path, arcname='admin/' + path.relative_to(dist).as_posix())
        remote_archive = '/tmp/yuanmall-admin-' + uuid.uuid4().hex + '.tar.gz'
        payload = {
            'archive': remote_archive,
            'sha256': hashlib.sha256(archive.read_bytes()).hexdigest(),
            'nginx': base64.b64encode((script_dir / 'nginx-locations.conf').read_bytes()).decode(),
        }
        subprocess.run(['scp', '-q', '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=10', str(archive), args.host + ':' + remote_archive], check=True, timeout=180)
        source = 'PAYLOAD=' + repr(base64.b64encode(json.dumps(payload).encode()).decode()) + '\n' + REMOTE
        result = subprocess.run(['ssh', '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=10', args.host, 'sudo -n python3 -'], input=source.encode(), stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=90)
        sys.stdout.buffer.write(result.stdout)
        sys.stderr.buffer.write(result.stderr)
        return result.returncode


if __name__ == '__main__':
    sys.exit(main())
