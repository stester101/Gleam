/* Gleam engine: the pure scheduling and money rules shared by Gleam 2 and the planner dashboard.
   No DOM, no storage, no globals beyond these functions. Loaded by index.html before its main script,
   and by tests/gleam-logic.test.js. Keep it dependency-free so the same file runs in a browser. */
const D=864e5;
const dt=s=>new Date(s+"T00:00:00");
function iso(d){ const z=new Date(d); z.setMinutes(z.getMinutes()-z.getTimezoneOffset()); return z.toISOString().slice(0,10); }
const TODAY=()=>iso(new Date());
const addD=(s,n)=>{ const p=s.split("-").map(Number); return new Date(Date.UTC(p[0],p[1]-1,p[2]+n)).toISOString().slice(0,10); };

/* money in a ledger entry: a clean counts charge minus what was taken; other types are signed by name */
function delta(h){
  const a=+h.a||0;
  switch(h.t){
    case "clean":     return a-(+h.p||0);
    case "opening":
    case "adjustment":
    case "charge":    return a;
    case "payment":
    case "writeoff":
    case "legacy_payment":
    case "legacy_writeoff": return -a;
    default:          return 0;   // price, skip, done, legacy_price
  }
}
const sortHist=h=>h.slice().sort((a,b)=> a.d<b.d?-1 : a.d>b.d?1 : (a.seqn||0)-(b.seqn||0));
function recalcBal(j){
  j.bal=Math.round(sortHist(j.hist||[]).reduce((b,h)=>b+delta(h),0)*100)/100;
}

/* next due date after a visit on onDate. Jobs with an anchor keep their original rhythm;
   on-request jobs (weeks 0) have no next date once done. */
function advanceNext(j,onDate){
  if(!(j.weeks>0)){ j.next=""; j.anchor=null; return; }
  const d=onDate||TODAY();
  if(j.anchor){
    let n=addD(j.anchor,j.weeks*7);
    while(dt(n)<=dt(d)) n=addD(n,j.weeks*7);
    j.next=n; j.anchor=null;
    return;
  }
  const basis=(j.next && dt(j.next)>dt(d))?j.next:d;
  j.next=addD(basis,j.weeks*7);
}

/* apply one clean from a widget or another device to one job.
   Rejects anything that isn't a clean, has no date, or repeats a clean already on that date.
   Returns true when the entry was added. seqn orders same-day history. */
function applyCleanEntry(j,e,seqn){
  if(!j||!e||e.t!=="clean"||!e.d) return false;
  j.hist=j.hist||[];
  if(j.hist.some(h=>h.id===e.id||(h.t==="clean"&&h.d===e.d))) return false;
  j.last=e.d;
  advanceNext(j,e.d);
  j.hist.push({id:e.id,d:e.d,seqn:seqn,t:"clean",a:+e.a||0,p:+e.p||0,m:e.m||"",n:e.n||""});
  recalcBal(j);
  return true;
}

/* money typed by a person: accepts "12,50", "12.50", "£12" */
function money(v){
  let t=String(v==null?"":v).trim();
  if(!t.includes(".")&&/\d,\d{1,2}$/.test(t)) t=t.replace(/,(\d{1,2})$/,".$1");   // comma used as the decimal point
  return Math.round((parseFloat(t.replace(/[^0-9.\-]/g,""))||0)*100)/100;
}

/* visits for one job between two dates (inclusive), in the same rules as Gleam's calendar.
   A repeating job projects forward from its next date; an overdue or one-off job only shows on its own day. */
function occurrencesBetween(j,start,end,today){
  const out=[];
  if(!j||!j.next||j.status!=="active") return out;
  const t=today||TODAY();
  if(j.weeks>0&&dt(j.next)>=dt(t)){
    let d=j.next,guard=0;
    while(d<=end&&guard++<800){ if(d>=start) out.push(d); d=addD(d,j.weeks*7); }
  }else if(j.next>=start&&j.next<=end){
    out.push(j.next);
  }
  return out;
}

/* move one visit. keep=true: the cycle carries on from the original date ("Keep rhythm").
   keep=false: the new date becomes the cycle from now on ("Shift schedule"). */
function applyMove(j,target,keep){
  if(j.weeks>0){ j.anchor=keep?(j.anchor||j.next||null):null; }
  j.next=target;
  return j;
}
