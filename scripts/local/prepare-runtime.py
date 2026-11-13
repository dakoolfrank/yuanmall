import pathlib
import secrets
import os
import urllib.parse
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parents[2]
local = root / '_local'
env_path = local / '.env'
if not env_path.exists():
    env_path.write_text(''.join(f'{key}={secrets.token_hex(24)}\n' for key in ('MYSQL_ROOT_PASSWORD', 'MYSQL_PASSWORD', 'REDIS_PASSWORD')), encoding='utf-8')
for name in ('logs', 'uploads'):
    (local / name).mkdir(exist_ok=True)
admin_env = root / 'yudao-ui-admin-vue3/.env.env.local.local'
if not admin_env.exists():
    admin_env.write_text('VITE_BASE_URL=http://127.0.0.1:48080\nVITE_OPEN=false\nVITE_APP_CAPTCHA_ENABLE=false\nVITE_APP_API_ENCRYPT_ENABLE=false\n', encoding='utf-8')
settings = ET.Element('settings', xmlns='http://maven.apache.org/SETTINGS/1.2.0')
mirror = ET.SubElement(ET.SubElement(settings, 'mirrors'), 'mirror')
for key, value in {'id': 'yuanmall-mirror', 'mirrorOf': '*', 'url': 'https://mirrors.huaweicloud.com/repository/maven/'}.items():
    ET.SubElement(mirror, key).text = value
proxy_url = os.environ.get('HTTPS_PROXY') or os.environ.get('HTTP_PROXY')
if proxy_url:
    parsed = urllib.parse.urlsplit(proxy_url)
    if parsed.scheme == 'http' and parsed.hostname:
        proxy = ET.SubElement(ET.SubElement(settings, 'proxies'), 'proxy')
        host = 'host.docker.internal' if parsed.hostname in ('localhost', '127.0.0.1') else parsed.hostname
        values = {'id': 'local-proxy', 'active': 'true', 'protocol': 'http', 'host': host, 'port': str(parsed.port or 80), 'nonProxyHosts': 'localhost|127.*|mysql|redis|backend'}
        if parsed.username:
            values['username'] = urllib.parse.unquote(parsed.username)
        if parsed.password:
            values['password'] = urllib.parse.unquote(parsed.password)
        for key, value in values.items():
            ET.SubElement(proxy, key).text = value
ET.ElementTree(settings).write(local / 'maven-settings.xml', encoding='utf-8', xml_declaration=True)
sql = '''UPDATE pay_app SET order_notify_url='http://backend:48080/app-api/trade/order/update-paid', refund_notify_url='http://backend:48080/admin-api/trade/after-sale/update-refunded' WHERE app_key='mall';
INSERT INTO pay_channel (code, status, fee_rate, remark, app_id, config, creator, updater, deleted, tenant_id)
SELECT 'mock', 0, 0, 'Local mock payment', id, '{"@class":"cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.NonePayClientConfig"}', '1', '1', b'0', tenant_id FROM pay_app WHERE app_key='mall' AND deleted=b'0';
UPDATE infra_file_config SET `master`=b'0';
INSERT INTO infra_file_config (id, name, storage, remark, `master`, config, creator, updater, deleted)
VALUES (9999, 'Local development files', 10, 'yuanmall local development', b'1', '{"@class":"cn.iocoder.yudao.module.infra.framework.file.core.client.local.LocalFileClientConfig","basePath":"/app/uploads","domain":"http://127.0.0.1:48080"}', '1', '1', b'0');
INSERT INTO member_user (mobile, password, status, nickname, register_ip, register_terminal, login_ip, creator, updater, tenant_id)
SELECT '19900000001', password, 0, 'Local buyer', '127.0.0.1', 20, '127.0.0.1', '1', '1', 1
FROM system_users WHERE username='admin' AND tenant_id=1 AND deleted=0
AND NOT EXISTS (SELECT 1 FROM member_user WHERE mobile='19900000001' AND tenant_id=1 AND deleted=0) LIMIT 1;
UPDATE system_sms_channel SET status=1;
'''
(local / 'db-local.sql').write_text(sql, encoding='utf-8')
print('LOCAL_ENV_AND_DATABASE_OVERRIDES_READY; credentials kept in ignored _local/.env')
