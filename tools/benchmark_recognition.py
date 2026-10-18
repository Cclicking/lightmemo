"""Compare the current recognizer with the pre-optimization Git version on one JVM.

Run: python tools/benchmark_recognition.py [--baseline HEAD]
Creates isolated, temporary test sources, runs an offline Gradle benchmark, and removes only
those generated files in finally. No API credentials are read and all HTTP is intercepted.
"""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", default="HEAD")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    commit = subprocess.check_output(["git", "rev-parse", "--verify", args.baseline + "^{commit}"], cwd=root, text=True).strip()
    subprocess.run(["git", "diff", "--exit-code", commit, "--", "app/src/main/assets"], cwd=root, check=True)
    source_dir = "app/src/main/java/com/click/lightmemo/network"
    sources = {
        name: subprocess.check_output(["git", "show", f"{commit}:{source_dir}/{name}.kt"], cwd=root).decode("utf-8")
        for name in ("FoodDataCentralClient", "FoodSearchMatching")
    }
    names = set()
    for source in sources.values():
        names.update(re.findall(r"^(?:(?:private|internal|public)\s+)?(?:(?:data|enum)\s+)?(?:class|object)\s+(\w+)", source, re.M))
    names_pattern = re.compile(r"\b(?:" + "|".join(re.escape(name) for name in sorted(names, key=len, reverse=True)) + r")\b")
    generated = root / "app/src/test/java/com/click/lightmemo/network/generatedbenchmark"
    if generated.exists():
        raise RuntimeError(f"Refusing to replace existing directory: {generated}")
    generated.mkdir(parents=True)
    files = []
    try:
        for name, source in sources.items():
            path = generated / f"Baseline{name}.kt"
            files.append(path)
            path.write_text(f"// Generated from {commit}; do not edit.\n" + names_pattern.sub(lambda match: "Baseline" + match[0], source), encoding="utf-8")
        benchmark = generated / "RecognitionSpeedBenchmark.kt"
        files.append(benchmark)
        shutil.copyfile(root / "tools/recognition-benchmark/RecognitionSpeedBenchmark.kt", benchmark)
        output = root / "app/build/recognition-benchmark/results.json"
        output.unlink(missing_ok=True)
        subprocess.run([
            str(root / ("gradlew.bat" if os.name == "nt" else "gradlew")),
            ":app:testDebugUnitTest", "--tests", "com.click.lightmemo.network.RecognitionSpeedBenchmark",
            "--console=plain", "--offline", "--rerun-tasks",
        ], cwd=root, check=True)
        results = json.loads(output.read_text(encoding="utf-8"))
        results["baselineCommit"] = commit
        destination = root / "build/recognition-benchmark"
        destination.mkdir(parents=True, exist_ok=True)
        (destination / "results.json").write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        lines = [
            "# 识别速度对比", "", f"基线：`{commit}`；对比当前工作区。",
            "同一 JVM 交替测量，先预热两轮；下表为多次运行的中位数，单位毫秒。",
            "真实离线库；网络场景使用固定延迟模拟响应，不访问互联网。", "",
            "| 场景 | 优化前 | 优化后 | 耗时减少 | 请求次数（前→后） |",
            "| --- | ---: | ---: | ---: | --- |",
        ]
        for case in results["cases"]:
            before, after = case["beforeMedianMs"], case["afterMedianMs"]
            reduced = (1 - after / before) * 100
            lines.append(f'| {case["name"]} | {before:.2f} | {after:.2f} | {reduced:.1f}% | {case["beforeRequests"]} → {case["afterRequests"]} |')
        lines += ["", "## 条件与边界", "", *[f"- {note}" for note in results["notes"]],
                  f'- JVM：{results["javaVersion"]}；系统：{results["os"]}；可用处理器：{results["processors"]}。',
                  "- 原始样本、各组样本数与 P95 保存在同目录 results.json；样本数量较少，P95 仅用于观察波动。",
                  "- 库冷加载指新客户端第一次加载数据库与索引；JVM 已预热，不包含应用进程启动，未清空系统文件缓存。",
                  "- 所有对比场景均核对候选顺序或匹配结果一致；耗时不包含 Gradle 编译和测试启动。",
                  "- 未测手机页面首帧、真实图片编解码及真实模型响应，不能据此承诺手机端到端提速比例。", ""]
        report = destination / "report.md"
        report.write_text("\n".join(lines), encoding="utf-8")
        print("\n".join(lines))
        print(f"Results: {report}")
    finally:
        for path in files:
            path.unlink(missing_ok=True)
        generated.rmdir()


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
