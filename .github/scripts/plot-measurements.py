#!/usr/bin/env python3
"""Generate plots from .github/test/measurements/conc-*/ data.

Usage:
    pip install -r .github/scripts/requirements-plot.txt
    python .github/scripts/plot-measurements.py [--measurements DIR] [--out DIR]
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

import matplotlib.pyplot as plt
import polars as pl
import seaborn as sns

CSV_COLUMNS = [
    "ts", "container", "cpu_perc",
    "mem_used_bytes", "mem_limit_bytes", "mem_perc",
    "net_rx_bytes", "net_tx_bytes",
    "block_r_bytes", "block_w_bytes", "pids",
]

AGENTS = ["fts-test-cd-agent-1", "fts-test-tc-agent-1", "fts-test-rd-agent-1"]
AGENT_LABEL = {
    "fts-test-cd-agent-1": "cd-agent",
    "fts-test-tc-agent-1": "tc-agent",
    "fts-test-rd-agent-1": "rd-agent",
}

BUCKET_SECONDS = 2

RUN_RE = re.compile(r"run-(\d+)-(\d+)\.csv$")
CONC_RE = re.compile(r"conc-(\d+)$")


def discover(root: Path) -> list[dict]:
    runs: list[dict] = []
    for conc_dir in sorted(root.glob("conc-*")):
        m = CONC_RE.search(conc_dir.name)
        if not m:
            continue
        concurrency = int(m.group(1))
        for csv_path in sorted(conc_dir.glob("run-*.csv")):
            rm = RUN_RE.search(csv_path.name)
            if not rm:
                continue
            size = int(rm.group(1))
            run_index = int(rm.group(2))
            meta_path = csv_path.with_suffix(".meta.json")
            if not meta_path.exists():
                continue
            meta = json.loads(meta_path.read_text())
            runs.append({
                "concurrency": concurrency,
                "size": size,
                "run_index": run_index,
                "csv": csv_path,
                "meta": meta,
            })
    return runs


def load_csvs(runs: list[dict]) -> pl.DataFrame:
    frames: list[pl.DataFrame] = []
    for r in runs:
        started = r["meta"]["started"]
        df = pl.read_csv(
            r["csv"],
            has_header=False,
            new_columns=CSV_COLUMNS,
            schema_overrides={
                "cpu_perc": pl.Float64, "mem_perc": pl.Float64,
                "mem_used_bytes": pl.Int64, "mem_limit_bytes": pl.Int64,
                "net_rx_bytes": pl.Int64, "net_tx_bytes": pl.Int64,
                "block_r_bytes": pl.Int64, "block_w_bytes": pl.Int64,
                "pids": pl.Int64,
            },
        ).filter(pl.col("container").is_in(AGENTS))
        if df.is_empty():
            continue
        df = df.with_columns(
            pl.col("ts").str.to_datetime(format="%Y-%m-%dT%H:%M:%SZ", time_zone="UTC"),
        ).with_columns(
            (
                (pl.col("ts") - pl.lit(started).str.to_datetime(format="%Y-%m-%dT%H:%M:%SZ", time_zone="UTC"))
                .dt.total_seconds()
            ).alias("t_sec_raw"),
        ).with_columns(
            ((pl.col("t_sec_raw") // BUCKET_SECONDS) * BUCKET_SECONDS).cast(pl.Int64).alias("t_sec"),
            pl.lit(r["concurrency"], dtype=pl.Int64).alias("concurrency"),
            pl.lit(r["size"], dtype=pl.Int64).alias("size"),
            pl.lit(r["run_index"], dtype=pl.Int64).alias("run_index"),
        ).with_columns(
            pl.col("container").replace(AGENT_LABEL).alias("agent"),
            (pl.col("mem_used_bytes") / (1024 * 1024)).alias("mem_used_mib"),
        )
        frames.append(df)
    if not frames:
        raise SystemExit("No measurement data found")
    return pl.concat(frames, how="vertical_relaxed")


def add_io_rates(df: pl.DataFrame) -> pl.DataFrame:
    """Cumulative byte counters → bytes/sec, partitioned per run/container."""
    keys = ["concurrency", "size", "run_index", "container"]
    df = df.sort(keys + ["t_sec"])
    cols = ["net_rx_bytes", "net_tx_bytes", "block_r_bytes", "block_w_bytes"]
    rate_exprs = []
    dt = pl.col("t_sec").diff().over(keys).cast(pl.Float64)
    for c in cols:
        delta = pl.col(c).diff().over(keys).cast(pl.Float64)
        rate = pl.when((dt > 0) & (delta >= 0)).then(delta / dt).otherwise(None)
        rate_exprs.append(rate.alias(f"{c}_rate"))
    return df.with_columns(rate_exprs).with_columns(
        (pl.col("net_rx_bytes_rate") / 1024).alias("net_rx_kibps"),
        (pl.col("net_tx_bytes_rate") / 1024).alias("net_tx_kibps"),
        (pl.col("block_r_bytes_rate") / 1024).alias("block_r_kibps"),
        (pl.col("block_w_bytes_rate") / 1024).alias("block_w_kibps"),
    )


def meta_throughput(runs: list[dict]) -> pl.DataFrame:
    rows = []
    for r in runs:
        ps = r["meta"]["processStatus"]
        dur = r["meta"]["durationSeconds"] or 1
        rows.append({
            "concurrency": r["concurrency"],
            "size": r["size"],
            "run_index": r["run_index"],
            "duration_seconds": dur,
            "total_bundles": ps["totalBundles"],
            "total_patients": ps["totalPatients"],
            "bundles_per_sec": ps["totalBundles"] / dur,
            "patients_per_sec": ps["totalPatients"] / dur,
        })
    return pl.DataFrame(rows)


def save(fig, base: Path) -> None:
    base.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(f"{base}.png", dpi=150, bbox_inches="tight")
    fig.savefig(f"{base}.svg", bbox_inches="tight")
    plt.close(fig)


def plot_timeseries(df: pl.DataFrame, value_col: str, ylabel: str, out: Path) -> None:
    pdf = df.select(["concurrency", "size", "run_index", "agent", "t_sec", value_col]).to_pandas()
    g = sns.relplot(
        data=pdf, kind="line",
        x="t_sec", y=value_col,
        hue="agent", row="concurrency", col="size",
        errorbar=None,
        height=3.0, aspect=1.6, facet_kws={"sharey": False, "sharex": False},
    )
    g.set_axis_labels("Seconds since run start", ylabel)
    g.set_titles("conc={row_name} | size={col_name}")
    g.figure.suptitle(f"{ylabel} over time", y=1.02)
    save(g.figure, out)


def plot_bars(df: pl.DataFrame, value_col: str, ylabel: str, out: Path) -> None:
    keys = ["concurrency", "size", "run_index", "agent"]
    per_run = df.group_by(keys).agg(
        pl.col(value_col).max().alias("peak"),
        pl.col(value_col).mean().alias("mean"),
    ).unpivot(
        index=keys, on=["peak", "mean"], variable_name="stat", value_name="value",
    )
    pdf = per_run.to_pandas()
    size_order = sorted(pdf["size"].unique().tolist())
    pdf["size"] = pdf["size"].astype(str)
    size_order_str = [str(s) for s in size_order]
    g = sns.catplot(
        data=pdf, kind="bar",
        x="size", y="value", hue="agent",
        row="concurrency", col="stat",
        order=size_order_str,
        errorbar=None,
        height=3.5, aspect=1.4, sharey=False,
    )
    g.set_axis_labels("Patient count", ylabel)
    g.set_titles("conc={row_name} | {col_name}")
    g.figure.suptitle(f"{ylabel} — peak and mean per run", y=1.02)
    save(g.figure, out)


def plot_throughput(meta_df: pl.DataFrame, out: Path) -> None:
    pdf = meta_df.unpivot(
        index=["concurrency", "size", "run_index"],
        on=["bundles_per_sec", "patients_per_sec"],
        variable_name="metric", value_name="rate",
    ).to_pandas()
    pdf["metric"] = pdf["metric"].map({
        "bundles_per_sec": "bundles/sec",
        "patients_per_sec": "patients/sec",
    })
    g = sns.relplot(
        data=pdf, kind="line",
        x="size", y="rate",
        hue="concurrency", style="concurrency",
        col="metric",
        markers=True, dashes=False,
        errorbar=None,
        height=4, aspect=1.3, facet_kws={"sharey": False},
    )
    for ax in g.axes.flat:
        ax.set_xscale("log")
    g.set_axis_labels("Patient count (log)", "rate")
    g.set_titles("{col_name}")
    g.figure.suptitle("End-to-end throughput", y=1.02)
    save(g.figure, out)


def plot_io(df: pl.DataFrame, value_cols: list[tuple[str, str]], title: str, out: Path) -> None:
    long = df.select(["concurrency", "size", "run_index", "agent", "t_sec", *[c for c, _ in value_cols]]).unpivot(
        index=["concurrency", "size", "run_index", "agent", "t_sec"],
        variable_name="direction",
        value_name="rate_kibps",
    )
    long = long.with_columns(
        pl.col("direction").replace({c: lbl for c, lbl in value_cols}),
    )
    pdf = long.drop_nulls("rate_kibps").to_pandas()
    g = sns.relplot(
        data=pdf, kind="line",
        x="t_sec", y="rate_kibps",
        hue="agent", style="direction",
        row="concurrency", col="size",
        errorbar=None,
        height=3.0, aspect=1.6, facet_kws={"sharey": False, "sharex": False},
    )
    g.set_axis_labels("Seconds since run start", "KiB/s")
    g.set_titles("conc={row_name} | size={col_name}")
    g.figure.suptitle(title, y=1.02)
    save(g.figure, out)


def plot_scaling_mem(df: pl.DataFrame, out: Path) -> None:
    keys = ["concurrency", "size", "run_index", "agent"]
    per_run = df.group_by(keys).agg(
        pl.col("mem_used_mib").max().alias("peak_mib"),
        pl.col("mem_used_mib").mean().alias("mean_mib"),
    ).unpivot(
        index=keys, on=["peak_mib", "mean_mib"], variable_name="stat", value_name="mem_mib",
    )
    pdf = per_run.to_pandas()
    pdf["stat"] = pdf["stat"].map({"peak_mib": "peak", "mean_mib": "mean"})
    size_order = sorted(pdf["size"].unique().tolist())
    pdf["size"] = pdf["size"].astype(str)
    size_order_str = [str(s) for s in size_order]
    g = sns.relplot(
        data=pdf, kind="line",
        x="concurrency", y="mem_mib",
        hue="size", style="size",
        hue_order=size_order_str, style_order=size_order_str,
        row="agent", col="stat",
        markers=True, dashes=False,
        errorbar=None,
        height=3.0, aspect=1.5, facet_kws={"sharey": False},
    )
    g.set_axis_labels("Send concurrency", "Memory (MiB)")
    g.set_titles("{row_name} | {col_name}")
    g.figure.suptitle("Memory usage vs send-concurrency", y=1.02)
    save(g.figure, out)


def wipe_outputs(out_dir: Path) -> None:
    if not out_dir.exists():
        return
    for ext in ("png", "svg"):
        for p in out_dir.glob(f"*.{ext}"):
            p.unlink()


def main() -> None:
    repo_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser()
    parser.add_argument("--measurements", type=Path,
                        default=repo_root / ".github/test/measurements")
    parser.add_argument("--out", type=Path,
                        default=repo_root / "docs/public/measurements")
    args = parser.parse_args()

    runs = discover(args.measurements)
    if not runs:
        raise SystemExit(f"No conc-*/run-*.csv under {args.measurements}")

    df = load_csvs(runs)
    df_io = add_io_rates(df)
    meta_df = meta_throughput(runs)

    sns.set_theme(context="talk", style="whitegrid", palette="tab10")
    wipe_outputs(args.out)
    args.out.mkdir(parents=True, exist_ok=True)

    plot_timeseries(df, "cpu_perc", "CPU (%)", args.out / "timeseries_cpu")
    plot_timeseries(df, "mem_used_mib", "Memory (MiB)", args.out / "timeseries_mem")
    plot_bars(df, "cpu_perc", "CPU (%)", args.out / "bars_cpu")
    plot_bars(df, "mem_used_mib", "Memory (MiB)", args.out / "bars_mem")
    plot_throughput(meta_df, args.out / "throughput")
    plot_io(df_io, [("net_rx_kibps", "rx"), ("net_tx_kibps", "tx")],
            "Network IO rate", args.out / "io_net")
    plot_io(df_io, [("block_r_kibps", "read"), ("block_w_kibps", "write")],
            "Disk IO rate", args.out / "io_disk")
    plot_scaling_mem(df, args.out / "scaling_mem")

    print(f"Wrote plots to {args.out}")


if __name__ == "__main__":
    main()
