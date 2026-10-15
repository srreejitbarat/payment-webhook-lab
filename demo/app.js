import {createLab} from './lab.js';
const $ = id => document.getElementById(id);
let lab = await createLab(), payment, currentEvent, timeline = [], busy = false, elapsed = 0;
const labels={success:'Send success',duplicate:'Replay same event',repeated:'New ID, same outcome',conflict:'Conflicting failure',tamper:'Tampered body',expired:'Expired timestamp'};
function row(cells){const tr=document.createElement('tr');for(const value of cells){const td=document.createElement('td');td.textContent=value;tr.append(td);}return tr;}
function note(title,message,bad=false){$('latest').className='latest '+(bad?'bad':'good');$('latest').querySelector('strong').textContent=title;$('latest').querySelector('p').textContent=message;}
function render(){
  $('receiptCount').textContent=lab.receipts.size;$('outboxCount').textContent=lab.outbox.length;$('deliveredCount').textContent=lab.outbox.filter(e=>e.state==='DELIVERED').length;
  $('paymentStatus').textContent=payment?.status??'NOT CREATED';$('paymentStatus').className='pill '+(payment?.status==='SUCCEEDED'?'ok':'');$('paymentId').textContent=payment?.paymentId??'Create a payment to begin';
  document.querySelectorAll('[data-action]').forEach(button=>button.disabled=!payment||busy);
  $('timeline').replaceChildren();if(!timeline.length){const tr=row(['Your requests will appear here.']);tr.className='empty';tr.firstChild.colSpan=3;$('timeline').append(tr);}
  for(const item of timeline.slice(0,30))$('timeline').append(row([item.action,String(item.code),item.result]));
  $('outbox').replaceChildren();$('outbox').className=lab.outbox.length?'':'outboxempty';if(!lab.outbox.length)$('outbox').textContent='No outbox events yet.';
  for(const event of lab.outbox.slice(-4)){const div=document.createElement('div');div.className='outboxrow';const code=document.createElement('code');code.textContent=event.eventId.slice(0,14)+'…';const state=document.createElement('span');state.textContent=`${event.state} · attempt ${event.attempts}`;div.append(code,state);$('outbox').append(div);}
  $('clock').textContent=`+${elapsed}s`;
}
$('amount').addEventListener('input',()=>{$('money').textContent=new Intl.NumberFormat('en-IN',{style:'currency',currency:'INR'}).format(Number($('amount').value)/100)+' · synthetic payment';});
$('create').addEventListener('click',()=>{try{payment=lab.create(Number($('amount').value));currentEvent=lab.event(payment);timeline.unshift({action:'Create payment',code:201,result:'CREATED'});note('Payment created','A signed success event is ready to send.');}catch(error){note('Invalid amount',error.message,true);}render();});
for(const button of document.querySelectorAll('[data-action]'))button.addEventListener('click',async()=>{
  if(!payment||busy)return;busy=true;render();const action=button.dataset.action;
  let event=currentEvent;
  if(action==='repeated')event=lab.event(payment);
  if(action==='conflict')event=lab.event(payment,'FAILED');
  try{const result=await lab.deliver(event,{tamper:action==='tamper',expired:action==='expired'});timeline.unshift({action:labels[action],code:200,result:result.disposition});
    const explanations={APPLIED:'Payment, receipt and one outbox event changed together in the simulation.',DUPLICATE:'The event was already received. No second business effect or outbox event.',IGNORED:'Same outcome, new event ID: receipt recorded, no second outbox event.'};
    note(`200 · ${result.disposition}`,explanations[result.disposition]);
  }catch(error){timeline.unshift({action:labels[action],code:error.status??500,result:'REJECTED'});note(`${error.status??500} · Rejected`,error.message,true);}
  finally{busy=false;render();}
});
$('dispatch').addEventListener('click',()=>{const event=lab.dispatch($('fail').checked);if(!event)note('No event is due','Pending retries may need the virtual clock to advance.');else note(event.state==='DELIVERED'?'Delivered to simulated sink':event.state==='DEAD'?'Retry limit reached':'Delivery failed',event.state==='PENDING'?`Attempt ${event.attempts}. Advance the virtual clock before retrying.`:`State ${event.state}; attempts ${event.attempts}.`,event.state!=='DELIVERED');render();});
$('advance').addEventListener('click',()=>{lab.advance(1000);elapsed++;render();});
$('reset').addEventListener('click',async()=>{if(busy)return;lab=await createLab();payment=undefined;currentEvent=undefined;timeline=[];elapsed=0;$('fail').checked=false;note('Lab reset','Create a payment to try another scenario.');render();});
render();
