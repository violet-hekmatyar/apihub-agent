# Apifox 测试资产说明

本目录用于保存 API-HUB Agent 的 Apifox 场景测试说明和导出报告。

## 当前核心报告

当前已完成的核心成功报告：

```text
docs/apifox/reports/01_scenario_runner_lecture_peak_30s_report.html
```

该报告证明的链路：

```text
Scenario Runner
-> Gateway Invoke
-> Mock Provider
-> gateway_log
-> scenario_run
-> scenario_call_sample
-> resultSummary / sample-calls
```

当前成功测试建议名称：

```text
01_流量生成链路_讲座报名高峰30s回归测试
```

## 目录说明

| 目录 | 说明 |
|---|---|
| `cases/` | 保存 Apifox 场景说明 |
| `reports/` | 保存最终成功报告 |
| `reports/failed/` | 保存测试配置迭代过程中的失败报告 |

`failed` 目录保存的是测试配置迭代记录，不代表最终系统失败。当前最终成功报告见 `reports/01_scenario_runner_lecture_peak_30s_report.html`。

当前后续开发重点不是继续增加 Apifox 场景，而是 Stats Aggregator v1。
