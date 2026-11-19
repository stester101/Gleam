// Planner v2 rules. Run: TZ=Europe/London node tests/planner-v2.test.js
const fs = require("fs"), path = require("path");
const root = path.join(__dirname, "..");
const engine = fs.readFileSync(path.join(root, "android/app/src/main/assets/gleam-engine.js"), "utf8");
const html = fs.readFileSync(path.join(root, "dashboard/planner-v2.html"), "utf8");
const pure = html.split("/*PURE-START*/")[1].split("/*PURE-END*/")[0];
const P = new Function(engine + "\n" + pure + "\nreturn {fmtMoney,fmtHours,weekStart,visitsByDay,hoursOn,completeVisit,moveVisit,mergeBackup,backupProblem};")();
let n = 0;
const ok = (c, m) => { if (!c) { console.error("FAIL " + m); process.exit(1); } n++; console.log("ok  " + m); };
const T = "2026-11-19"; // Thursday
const mk = (o) => Object.assign({ id: "j", name: "A", price: 20, mins: 60, weeks: 4, next: "2026-11-19", status: "active", hist: [], bal: 0 }, o);

ok(P.fmtMoney(1234.5) === "£1.234,50" && P.fmtMoney(0) === "£0,00" && P.fmtMoney(-5) === "-£5,00", "money format comma decimal");
ok(P.fmtHours(4.5) === "4,5 h", "hours format");
ok(P.weekStart("2026-11-19") === "2026-11-16" && P.weekStart("2026-11-22") === "2026-11-16", "week starts Monday");

let j = mk({}); let v = P.visitsByDay([j], "2026-11-16", "2027-01-31", T);
ok(v["2026-11-19"][0].first && v["2026-12-17"][0] && !v["2026-12-17"][0].first, "repeats projected, only next is first");
ok(P.hoursOn([j], v["2026-11-19"]) === 1, "capacity in hours");

ok(!P.completeVisit(mk({ next: "2026-11-20" }), "2026-11-20", "paid", 0, T).ok, "future visit cannot be completed");
ok(!P.completeVisit(j, "2026-12-17", "paid", 0, T).ok, "later repeat cannot be completed");
j = mk({}); ok(P.completeVisit(j, T, "paid", 0, T).ok && j.bal === 0 && j.next === "2026-12-17" && j.last === T, "paid: balance 0, next +4w");
j = mk({}); P.completeVisit(j, T, "unpaid", 0, T); ok(j.bal === 20, "not paid: balance 20");
j = mk({}); ok(P.completeVisit(j, T, "part", "7,50", T).ok && j.bal === 12.5, "part paid with comma amount");
j = mk({}); ok(!P.completeVisit(j, T, "part", "25", T).ok && j.hist.length === 0, "part paid must be below price");
j = mk({ next: "2026-11-10" }); ok(P.completeVisit(j, "2026-11-10", "paid", 0, T).ok, "overdue visit can be completed");

j = mk({}); ok(!P.moveVisit(j, T, "2026-11-18", true, T).ok, "no move into the past");
j = mk({}); P.moveVisit(j, T, "2026-11-21", true, T); ok(j.next === "2026-11-21" && j.anchor === "2026-11-19", "keep rhythm sets anchor");
P.completeVisit(j, "2026-11-21", "paid", 0, "2026-11-21"); ok(j.next === "2026-12-17", "keep rhythm: next stays on old pattern");
j = mk({}); P.moveVisit(j, T, "2026-11-21", false, T); ok(j.next === "2026-11-21" && !j.anchor, "shift clears anchor");

const L = { jobs: [mk({ id: "a", next: "2026-11-19", hist: [{ id: "h1", d: "2026-01-01", t: "opening", a: 5 }] })] };
const inc = { jobs: [mk({ id: "a", next: "2026-11-25", hist: [{ id: "h1", d: "2026-01-01", t: "opening", a: 5 }, { id: "h2", d: "2026-11-01", t: "clean", a: 20, p: 0 }] }), mk({ id: "b" })] };
const o = P.mergeBackup(L, inc);
ok(L.jobs.length === 2 && o.added === 1 && o.hist === 1 && o.flagged === 1, "merge adds job, joins history, flags clash");
ok(L.jobs[0].next === "2026-11-19" && L.jobs[0]._clash && L.jobs[0].bal === 25, "merge keeps local change and flags it");
ok(P.backupProblem({}) !== "" && P.backupProblem(inc) === "", "backup validation");
console.log(n + " ok");
