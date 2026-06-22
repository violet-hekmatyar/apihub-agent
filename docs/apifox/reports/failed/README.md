# Apifox 失败报告说明

本目录保存 Scenario Runner Apifox 自动化测试配置迭代过程中的失败报告。这些报告用于记录变量传递、参数配置和断言兼容性问题，不代表最终系统失败。

最终成功报告见上一层目录：

```text
docs/apifox/reports/01_scenario_runner_lecture_peak_30s_report.html
```

| 文件 | 问题类型 | 说明 | 当前状态 |
|---|---|---|---|
| `01_scenario_runner_lecture_peak_30s_failed_path_variable_empty_20260621_165816.html` | 路径变量为空 | `scenarioRunId` 路径变量为空，导致 `/scenario-runs//result` 这类请求失败；同时暴露了部分结果断言需要与实际响应结构对齐。 | 已通过修正 Apifox 变量传递解决 |
| `02_scenario_runner_lecture_peak_30s_failed_sample_calls_param_20260621_171526.html` | 参数/断言不兼容 | `sample-calls` 参数为空或测试断言未兼容 `data.samples` 响应结构，导致样本查询相关断言失败。 | 已通过固定 `limit` 参数并兼容 `data.samples` 结构解决 |

当前结论：失败报告是测试配置迭代记录；最终以 `01_scenario_runner_lecture_peak_30s_report.html` 成功报告为准。
