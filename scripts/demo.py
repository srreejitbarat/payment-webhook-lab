"""Exercise a real running server with synthetic data. Requires only Python 3."""
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError
import hashlib, hmac, json, os, time

root = Path(__file__).resolve().parents[1]
settings = {}
if (root / '.env').exists():
    settings = dict(line.split('=', 1) for line in (root / '.env').read_text().splitlines() if '=' in line)
settings.update(os.environ)
base = settings.get('PAYMENT_BASE_URL', 'http://127.0.0.1:8080').rstrip('/')
key = settings['PAYMENT_API_KEY']
secret = settings['WEBHOOK_SECRET']

def request(path, value=None, webhook=False, forged=False):
    body = None if value is None else json.dumps(value, separators=(',', ':')).encode()
    headers = {'Content-Type': 'application/json'}
    if webhook:
        ts = str(int(time.time()))
        headers['X-Webhook-Timestamp'] = ts
        headers['X-Webhook-Signature'] = ('00' * 32 if forged else hmac.new(secret.encode(), ts.encode()+b'.'+body, hashlib.sha256).hexdigest())
    else:
        headers['X-Api-Key'] = key
    try:
        with urlopen(Request(base+path, data=body, headers=headers), timeout=10) as response:
            return response.status, json.loads(response.read())
    except HTTPError as error:
        return error.code, json.loads(error.read())

code, payment = request('/api/payments', {'amountMinor': 1000, 'currency': 'INR'})
assert code == 201, (code, payment)
event = {'eventId': 'demo-'+payment['paymentId'], 'paymentId': payment['paymentId'], 'amountMinor': 1000, 'currency': 'INR', 'status': 'SUCCEEDED'}
assert request('/webhooks/payments', event, webhook=True, forged=True)[0] == 401
assert request('/webhooks/payments', event, webhook=True)[1]['disposition'] == 'APPLIED'
assert request('/webhooks/payments', event, webhook=True)[1]['disposition'] == 'DUPLICATE'
event['eventId'] += '-replayed-outcome'
assert request('/webhooks/payments', event, webhook=True)[1]['disposition'] == 'IGNORED'
event['eventId'] += '-contradiction'; event['status'] = 'FAILED'
assert request('/webhooks/payments', event, webhook=True)[0] == 409
assert request('/api/payments/'+payment['paymentId'])[1]['status'] == 'SUCCEEDED'
print('PASS: create, forged signature rejection, apply, duplicate, repeated outcome and terminal-state conflict.')
print('Synthetic payment ID:', payment['paymentId'])
