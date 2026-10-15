export class ApiError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}
const bytes = text => new TextEncoder().encode(text);
const hex = data => [...new Uint8Array(data)].map(v => v.toString(16).padStart(2, '0')).join('');
export async function createLab() {
  const secret = await crypto.subtle.generateKey({name: 'HMAC', hash: 'SHA-256'}, false, ['sign', 'verify']);
  const payments = new Map(), receipts = new Map(), outbox = [];
  let now = Date.now();
  async function sign(body, timestamp) { return hex(await crypto.subtle.sign('HMAC', secret, bytes(timestamp + '.' + body))); }
  async function accept(body, timestamp, signature) {
    if (Math.abs(Number(timestamp) * 1000 - now) > 300000 || !/^[a-f\d]{64}$/i.test(signature))
      throw new ApiError(401, 'Invalid or expired webhook signature');
    const sig = Uint8Array.from(signature.match(/../g), v => parseInt(v, 16));
    if (!await crypto.subtle.verify('HMAC', secret, sig, bytes(timestamp + '.' + body)))
      throw new ApiError(401, 'Invalid or expired webhook signature');
    let event;
    try { event = JSON.parse(body); } catch { throw new ApiError(400, 'Invalid JSON'); }
    if (!event || !['SUCCEEDED', 'FAILED'].includes(event.status) || !event.eventId) throw new ApiError(400, 'Invalid event');
    const payment = payments.get(event.paymentId);
    if (!payment) throw new ApiError(404, 'Payment not found');
    const previous = receipts.get(event.eventId);
    if (previous) {
      const fields = ['eventId','paymentId','amountMinor','currency','status'];
      if (!fields.every(key => previous[key] === event[key])) throw new ApiError(409, 'Event ID was used with different content');
      return {disposition:'DUPLICATE', paymentId:payment.paymentId, status:payment.status};
    }
    if (event.amountMinor !== payment.amountMinor || event.currency !== payment.currency)
      throw new ApiError(409, 'Event amount or currency does not match payment');
    if (payment.status !== 'CREATED' && payment.status !== event.status)
      throw new ApiError(409, 'A terminal payment cannot change to a different terminal state');
    const apply = payment.status === 'CREATED';
    receipts.set(event.eventId, {...event});
    if (apply) {
      payment.status = event.status;
      outbox.push({eventId:event.eventId, paymentId:payment.paymentId, state:'PENDING', attempts:0, nextAttempt:now});
    }
    return {disposition:apply ? 'APPLIED' : 'IGNORED', paymentId:payment.paymentId, status:payment.status};
  }
  return {
    payments, receipts, outbox,
    get now() { return now; },
    advance(ms) { now += ms; },
    create(amountMinor) {
      if (!Number.isSafeInteger(amountMinor) || amountMinor < 1 || amountMinor > 9999999999999) throw new ApiError(400, 'Enter a positive integer amount in minor units');
      const payment = {paymentId:crypto.randomUUID(), amountMinor, currency:'INR', status:'CREATED'};
      payments.set(payment.paymentId, payment); return payment;
    },
    event(payment, status='SUCCEEDED', eventId='evt-'+crypto.randomUUID()) {
      return {eventId, paymentId:payment.paymentId, amountMinor:payment.amountMinor, currency:payment.currency, status};
    },
    sign, accept,
    async deliver(event, {tamper=false, expired=false}={}) {
      const body = JSON.stringify(event), timestamp = String(Math.floor(now/1000) - (expired ? 301 : 0));
      const signature = await sign(body, timestamp);
      return accept(tamper ? body+' ' : body, timestamp, signature);
    },
    dispatch(fail=false) {
      const event = outbox.filter(e => e.state === 'PENDING' && e.nextAttempt <= now).sort((a,b) => a.nextAttempt-b.nextAttempt)[0];
      if (!event) return null;
      event.attempts++;
      if (!fail) event.state = 'DELIVERED';
      else {
        event.state = event.attempts >= 3 ? 'DEAD' : 'PENDING';
        event.nextAttempt = now + Math.min(60000, 1000 * 2 ** (event.attempts-1));
      }
      return {...event};
    }
  };
}
