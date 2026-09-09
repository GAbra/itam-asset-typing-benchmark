"""Render publication figures from archived JSON; --check detects stale SVGs."""
import argparse
import io
import json
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from matplotlib.ticker import FuncFormatter

ROOT = Path(__file__).resolve().parents[1]
SIZES = (10_000, 100_000, 500_000, 1_000_000)
ENGINES = ("HASHMAP_BITSET", "CEL", "DMN_KIE")
LABELS = ("HashMap + BitSet", "CEL", "DMN / KIE")
COLORS = ("#0f766e", "#2563eb", "#b45309")
plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 11,
                     "svg.fonttype": "path", "svg.hashsalt": "itam-benchmark-v1",
                     "axes.spines.top": False, "axes.spines.right": False,
                     "axes.edgecolor": "#cbd5e1", "text.color": "#0f172a",
                     "axes.labelcolor": "#334155", "xtick.color": "#475569",
                     "ytick.color": "#475569", "figure.facecolor": "white"})


def save(fig, name, check):
    buffer = io.BytesIO()
    fig.savefig(buffer, format="svg", metadata={"Date": None, "Creator": "ITAM benchmark figure generator"})
    content = b"\n".join(line.rstrip() for line in buffer.getvalue().splitlines()) + b"\n"
    path = ROOT / "docs" / "assets" / name
    if check:
        if not path.exists() or path.read_bytes() != content:
            raise SystemExit(f"Stale figure: {path.name}; run python scripts/render-results.py")
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        preview = ROOT / "results" / "figure-preview"
        preview.mkdir(parents=True, exist_ok=True)
        fig.savefig(preview / name.replace(".svg", ".png"), dpi=140)
    plt.close(fig)
    print(f"{'Checked' if check else 'Rendered'} {name}")


def frame(title, subtitle):
    fig, ax = plt.subplots(figsize=(10, 4.8))
    fig.subplots_adjust(left=0.14, right=0.95, bottom=0.2, top=0.76)
    fig.text(0.07, 0.92, title, fontsize=19, weight="bold")
    fig.text(0.07, 0.85, subtitle, fontsize=10, color="#475569")
    fig.text(0.07, 0.045, "Archived synthetic baseline · 14 rules · original host/JVM environment not recorded",
             fontsize=9, color="#64748b")
    ax.set_axisbelow(True)
    return fig, ax


def main():
    check = argparse.ArgumentParser()
    check.add_argument("--check", action="store_true")
    args = check.parse_args()
    reports = [json.loads((ROOT / "benchmark-results" / f"benchmark-{n}.json").read_text()) for n in SIZES]
    summaries = [{s["engine"]: s for s in report["summary"]} for report in reports]
    fig, ax = frame("Throughput as asset count grows", "Median of 5 passes; whiskers show observed min–max. Higher is faster.")
    x = np.arange(len(SIZES))
    for engine, label, color, marker in zip(ENGINES, LABELS, COLORS, ("o", "s", "^")):
        middle = np.array([s[engine]["medianAssetsPerSecond"] for s in summaries])
        low = np.array([s[engine]["minAssetsPerSecond"] for s in summaries])
        high = np.array([s[engine]["maxAssetsPerSecond"] for s in summaries])
        ax.errorbar(x, middle, yerr=[middle-low, high-middle], label=label, color=color,
                    marker=marker, linewidth=2.3, markersize=7, capsize=4)
    ax.set_xticks(x, ("10K", "100K", "500K", "1M"))
    ax.set_xlabel("Assets (discrete experiment sizes)")
    ax.set_ylabel("Assets / second")
    ax.set_ylim(bottom=0)
    ax.yaxis.set_major_formatter(FuncFormatter(lambda value, _: f"{value/1000:.0f}K"))
    ax.grid(axis="y", color="#e2e8f0")
    ax.legend(frameon=False, loc="lower left", bbox_to_anchor=(0, 1.01), ncol=3, fontsize=10)
    save(fig, "throughput.svg", args.check)
    for key, name, title, subtitle, unit in (
            ("medianNsPerAsset", "ns-per-asset.svg", "Time per asset at 1M records",
             "Batch-derived mean time per asset, median of 5 passes. Lower is faster.", "µs / asset"),
            (None, "startup.svg", "Observed engine initialization",
             "One observation per engine in one shared JVM; fixed order. Not an isolated cold-start test.", "milliseconds")):
        values = [summaries[-1][e][key] / 1000 if key else reports[-1]["engineLoadMs"][e] for e in ENGINES]
        fig, ax = frame(title, subtitle)
        fig.subplots_adjust(left=0.24)
        ax.barh(LABELS, values, color=COLORS, height=0.5)
        ax.invert_yaxis()
        ax.set_xlim(0, max(values)*1.23)
        ax.set_xlabel(unit)
        ax.grid(axis="x", color="#e2e8f0")
        for i, value in enumerate(values):
            ax.text(value + max(values)*0.025, i, f"{value:.3f}" if key else f"{value:.1f}", va="center", weight="bold")
        save(fig, name, args.check)


if __name__ == "__main__":
    main()
