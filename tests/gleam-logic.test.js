// Targeted logic tests for the embedded Gleam app (ledger, scheduling, widget op replay, money).
// Run: TZ=Europe/London node tests/gleam-logic.test.js android/app/src/main/assets/index.html
const fs = require("fs");
const html = fs.readFileSync(process.argv[2], "utf8");
const js = html.split("<script>").pop().split("</script>")[0];
const grab = (re) => { const m = js.match(re); if (!m) throw new Error("missing " + re); return m[0]; };
const fnSrc = (name) => {
  const start = js.indexOf("function " + name + "(");
  if (start < 0) throw new Error("missing fn " + name);
  let i = js.indexOf("{", start), depth = 0;
  for (; i < js.length; i++) { if (js[i] === "{") depth++; else if (js[i] === "}" && --depth === 0) break; }
  return js.slice(start, i + 1);
};
const helpers = ["iso", "delta", "recalcBal", "advanceNext", "applyNativeOp"].map(fnSrc).join("\n");
const consts = [/const D=.*?;/, /const TODAY=.*;/, /const sortHist=.*;/, /const dt=.*;/, /const addD=.*;/].map(grab).join("\n");
const S = { jobs: [{ id: "j1", price: 20, weeks: 4, next: "2026-11-12", last: "2026-10-15", hist: [{ id: "o", d: "2026-01-01", t: "opening", a: 5 }] }] };
const run = new Function("S", `let HSEQ=0;\n${consts}\n${helpers}\nreturn {applyNativeOp,recalcBal};`)(S);
const j = S.jobs[0]; run.recalcBal(j);
const op = { id: "op1", job: "j1", entry: { id: "hnative1", d: "2026-11-12", t: "clean", a: 20, p: 0, n: "Not paid" } };
const ok = (c, m) => { if (!c) { console.error("FAIL " + m); process.exit(1); } console.log("ok  " + m); };
ok(j.bal === 5, "opening balance 5");
ok(run.applyNativeOp(op) === true, "op applied");
ok(j.bal === 25 && j.next === "2026-12-10" && j.last === "2026-11-12", "unpaid clean: bal 25, next +4w");
ok(run.applyNativeOp(op) === false && j.hist.length === 2, "same op replay is a no-op");
const op2 = { id: "op2", job: "j1", entry: { id: "hnative2", d: "2026-11-12", t: "clean", a: 20, p: 20, n: "Paid" } };
ok(run.applyNativeOp(op2) === false && j.bal === 25, "second clean same day rejected");
ok(run.applyNativeOp({ id: "x", job: "nope", entry: op.entry }) === false, "unknown job ignored");
// --- batch A additions ---
const run2 = new Function("S", `let HSEQ=0;\n${consts}\n${helpers}\nreturn {addD,advanceNext};`)(S);
ok(run2.addD("2026-10-21",7)==="2026-10-28", "addD across autumn clock change");
ok(run2.addD("2026-10-25",1)==="2026-10-26", "addD steps past 25 Oct");
ok(run2.addD("2026-03-30",-1)==="2026-03-29", "addD backwards across spring change");
const k = { weeks: 4, next: "2026-10-21", anchor: null }; run2.advanceNext(k, "2026-10-21");
ok(k.next==="2026-11-18", "4-weekly across DST lands on same weekday");
const o = { weeks: 0, next: "2026-11-12" }; run2.advanceNext(o, "2026-11-12");
ok(o.next==="", "on-request job no longer due after completion");
// --- batch C additions ---
const moneySrc = js.match(/const money=v=>\{[\s\S]*?\n\};/)[0];
const money = new Function(moneySrc + "\nreturn money;")();
ok(money("12,50")===12.5, "money 12,50 -> 12.5");
ok(money("£1,234.50")===1234.5, "money £1,234.50");
ok(money("1,234")===1234, "money 1,234 thousands");
ok(money("-12.00")===-12, "money negative");
