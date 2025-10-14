#!/usr/bin/env python3
"""
gen_testplan.py — Auto-generate a test plan skeleton for an Android (Java) project.

Usage:
  python scripts/gen_testplan.py --project-root . --out-dir ./testplan_out
  (Run from your repo root.)

What it does:
- Walks app/src/main/java for .java files
- Classifies files into categories (Activity/Adapter/Model/Util/API/...)
- Emits suggested test items per file (generic + file-specific hints)
- Writes Markdown and CSV outputs you can immediately share

This is a static doc generator: it doesn't touch your DB/Firestore.
"""
import argparse
import re
from pathlib import Path
import csv
import sys

# ---- Classification helpers -------------------------------------------------

MODEL_NAMES = {"Trip.java","Bill.java","ParticipantBalance.java","ParticipantSplit.java","TripMember.java"}

GENERIC_TEMPLATES = {
    "Activity (UI flow)": [
        "Launch renders without crash (Robolectric/Instrumentation).",
        "Toolbar/nav buttons respond (Back/Home).",
        "Empty state renders with no data.",
        "Form validation: required fields, formats (email, amount).",
        "Intent extras parsed correctly; missing/invalid extras handled.",
        "Lifecycle: rotate screen, background/foreground preserves state.",
        "Permissions flow (if applicable): request/deny/grant paths.",
        "Happy path navigation to next screen (Intent).",
        "Error handling: API/DB failure shows toast/snackbar/dialog."
    ],
    "Adapter (RecyclerView/List)": [
        "onCreateViewHolder inflates correct layout.",
        "onBindViewHolder binds all fields for typical item.",
        "List updates notifyDataSetChanged / DiffUtil emits correct changes.",
        "Click listeners (item/child) invoke expected callbacks.",
        "Empty/short/long data sets render without crash."
    ],
    "Model/Data": [
        "Field defaults and nullability.",
        "Serialization/deserialization (e.g., Firestore/Bundle/JSON).",
        "Equality/hashCode if used in sets/maps.",
        "Date/time/amount formatting utilities."
    ],
    "API/Service/DTO": [
        "Request building with expected params/headers.",
        "Response DTO mapping from sample JSON.",
        "Network failure handling and retries (if implemented)."
    ],
    "Utility": [
        "Pure functions yield expected outputs for typical/edge cases.",
        "Locale/currency formatting adheres to target settings (e.g., en-AU)."
    ],
    "Other": [
        "General unit tests based on class behavior or collaborators."
    ],
    "Test": [
        "Existing tests compile and run; add more coverage."
    ]
}

FILE_HINTS = {
    "AddTripActivity.java": [
        "Create trip: required fields, date range validity (start ≤ end).",
        "Location field: manual vs geocoded input.",
        "Tripmates add/remove; duplicate email blocked."
    ],
    "EditTripActivity.java": [
        "Persist edited fields; unchanged fields remain.",
        "Cancel vs Save navigation outcomes."
    ],
    "TripDetailActivity.java": [
        "Bills list displays; totals/balances recompute on change.",
        "Realtime listener update UI on Firestore changes."
    ],
    "AddBillActivity.java": [
        "Payer selection; split strategies (equal/custom).",
        "Currency/amount validation; negative/zero blocked.",
        "Photo receipt attach/preview (gallery/camera)."
    ],
    "EditBillActivity.java": [
        "Edit line items and participants; recompute splits.",
        "Deleting/restoring bills behavior."
    ],
    "BillsAdapter.java": [
        "Formatting of amounts; highlight payer/user debts."
    ],
    "SplitAmountAdapter.java": [
        "Custom split input syncs with data model; validation."
    ],
    "PayerSelectionActivity.java": [
        "Only one payer selected; default selection rules."
    ],
    "ParticipantsSelectionActivity.java": [
        "Multi-select; search/filter; already-added state."
    ],
    "HomeActivity.java": [
        "Bottom nav switches tabs; recent trips feed; fab actions."
    ],
    "TripAdapter.java": [
        "Trip card date/location renders; click navigates to details."
    ],
    "ProfileActivity.java": [
        "Change name flow; sign out; avatar placeholder."
    ],
    "SignInActivity.java": [
        "Email/password validation; FirebaseAuth errors surfaced."
    ],
    "SignUpActivity.java": [
        "Password strength/confirm match; duplicate email error."
    ],
    "ForgotPasswordActivity.java": [
        "Sends reset email; invalid email guarded."
    ],
    "SplashActivity.java": [
        "Auth gate routes anonymous vs signed-in users."
    ],
    "ReceiptOcrParser.java": [
        "OCR input parse stability; sample receipts mapping."
    ],
    "BleUidExchange.java": [
        "BLE permission gating (Android 12+); scan/advertise lifecycle.",
        "No-ble devices fallback.",
        "Foreground/background behavior."
    ],
    "CurrencyUtils.java": [
        "Format amounts for locale; rounding; edge cases."
    ],
    "AlgorithmTestActivity.java": [
        "Algorithm correctness on sample datasets; time complexity sanity."
    ]
}

def classify(filename: str) -> str:
    if filename.endswith("Activity.java"):
        return "Activity (UI flow)"
    if filename.endswith("Adapter.java"):
        return "Adapter (RecyclerView/List)"
    if filename.endswith("Service.java") or filename.endswith("Response.java"):
        return "API/Service/DTO"
    low = filename.lower()
    if low.endswith("utils.java") or low.endswith("util.java"):
        return "Utility"
    if filename in MODEL_NAMES:
        return "Model/Data"
    if filename.endswith("Test.java"):
        return "Test"
    return "Other"

# ---- Core generation --------------------------------------------------------

def collect_java_files(project_root: Path):
    src = project_root / "app" / "src" / "main" / "java"
    files = []
    if not src.exists():
        print(f"[WARN] Source directory not found: {src}", file=sys.stderr)
        return files
    for p in src.rglob("*.java"):
        files.append(p)
    return sorted(files)

def render_markdown(rows, out_md: Path):
    # rows: list of dicts with keys: category, file, relpath, tests(list[str])
    lines = []
    lines.append("# Auto-Generated Test Plan (Skeleton)\n")
    lines.append("_Generated by scripts/gen_testplan.py_\n")
    lines.append("## How to use\n- Start from these suggested checks, add **Preconditions / Steps / Expected Results / Priority**.\n- For Activities use **Robolectric/Instrumentation**; for pure logic use **JUnit + Mockito**.\n- Prefer **Firebase Emulator Suite** over production during tests.\n")

    # group by category
    by_cat = {}
    for r in rows:
        by_cat.setdefault(r["category"], []).append(r)
    for cat in sorted(by_cat.keys()):
        lines.append(f"## {cat}\n")
        for r in sorted(by_cat[cat], key=lambda x: x["file"]):
            rel = r["relpath"]
            lines.append(f"### {r['file']}\n")
            lines.append(f"- Path: `{rel}`")
            for t in r["tests"]:
                lines.append(f"- {t}")
            lines.append("")
    out_md.write_text("\n".join(lines), encoding="utf-8")

def render_csv(rows, out_csv: Path):
    # Flatten into columns: Module/Category, File, RelPath, Test Title
    with out_csv.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["Module/Category", "File", "RelPath", "Suggested Test Item"])
        for r in rows:
            for t in r["tests"]:
                w.writerow([r["category"], r["file"], r["relpath"], t])

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--project-root", default=".", help="Path to the Android project root (contains app/)")
    ap.add_argument("--out-dir", default="./testplan_out", help="Where to write outputs")
    args = ap.parse_args()

    project_root = Path(args.project_root).resolve()
    out_dir = Path(args.out_dir).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    java_files = collect_java_files(project_root)
    if not java_files:
        print("[INFO] No Java files found under app/src/main/java. Did you pass the correct --project-root?", file=sys.stderr)

    rows = []
    for p in java_files:
        file = p.name
        rel = p.relative_to(project_root).as_posix()
        cat = classify(file)
        tests = []
        tests.extend(FILE_HINTS.get(file, []))
        tests.extend(GENERIC_TEMPLATES.get(cat, []))
        if not tests:
            tests.append("Add class-appropriate tests based on behavior and collaborators.")
        rows.append({
            "category": cat,
            "file": file,
            "relpath": rel,
            "tests": tests
        })

    # Write outputs
    md_path = out_dir / "TestPlan_Skeleton.md"
    csv_path = out_dir / "TestPlan_Skeleton.csv"
    render_markdown(rows, md_path)
    render_csv(rows, csv_path)

    print(f"[OK] Wrote:\n- {md_path}\n- {csv_path}")

if __name__ == "__main__":
    main()
