"""Create fresh local demo credentials; never print them or overwrite existing values."""
from pathlib import Path
import secrets

path = Path(__file__).resolve().parents[1] / '.env'
if path.exists():
    raise SystemExit('.env already exists; preserved it.')
path.write_text('\n'.join(f'{key}={secrets.token_hex(32)}' for key in
    ['PAYMENT_API_KEY', 'WEBHOOK_SECRET', 'DB_PASSWORD']) + '\n')
path.chmod(0o600)
print('Created .env with fresh demo values. It is excluded from Git and Docker build context.')
