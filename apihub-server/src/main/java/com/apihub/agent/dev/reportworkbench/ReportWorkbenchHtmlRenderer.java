package com.apihub.agent.dev.reportworkbench;

import com.apihub.agent.model.vo.AgentReportDetailVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class ReportWorkbenchHtmlRenderer {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;

    ReportWorkbenchHtmlRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String render(AgentReportDetailVO detail) {
        Map<String, Object> report = detail == null || detail.getReport() == null ? Map.of() : detail.getReport();
        Map<String, Object> extra = parseMap(report.get("extraInfo"));
        Map<String, Object> htmlJson = parseMap(extra.get("htmlRenderableJson"));
        if (htmlJson.isEmpty()) {
            htmlJson = fallbackJson(report);
        }

        Map<String, Object> header = map(htmlJson.get("reportHeader"));
        Map<String, Object> status = map(htmlJson.get("displayStatus"));
        String statusCode = value(status.get("status"), "UNKNOWN");
        String colorLevel = value(status.get("colorLevel"), "GRAY");

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>API-HUB Monitor Report ")
                .append(escape(header.getOrDefault("reportCode", report.get("reportId")))).append("</title>")
                .append("<style>")
                .append("""
                        :root{color-scheme:light;--text:#1f2937;--muted:#6b7280;--line:#d8dee9;--bg:#f7f9fc;--panel:#fff;--green:#18864b;--blue:#2563eb;--yellow:#9a6a00;--gray:#64748b;}
                        *{box-sizing:border-box}
                        body{margin:0;background:var(--bg);color:var(--text);font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","Microsoft YaHei",Arial,sans-serif;line-height:1.6}
                        .page{max-width:1180px;margin:0 auto;padding:28px 20px 48px}
                        .top{border-bottom:3px solid #dbeafe;padding-bottom:18px;margin-bottom:20px}
                        h1{font-size:26px;margin:0 0 10px;letter-spacing:0}
                        h2{font-size:18px;margin:28px 0 12px;border-left:4px solid #93c5fd;padding-left:10px}
                        .subtitle{color:var(--muted);font-size:13px}
                        .status{display:inline-flex;align-items:center;gap:8px;border:1px solid var(--line);border-radius:8px;padding:8px 12px;background:var(--panel);font-weight:650}
                        .status.GREEN{color:var(--green);border-color:#a7f3d0}.status.BLUE{color:var(--blue);border-color:#bfdbfe}.status.YELLOW{color:var(--yellow);border-color:#fde68a}.status.GRAY{color:var(--gray);border-color:#cbd5e1}
                        .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:10px}
                        .kv{background:var(--panel);border:1px solid var(--line);border-radius:8px;padding:10px 12px;min-width:0}
                        .k{font-size:12px;color:var(--muted);margin-bottom:3px}.v{font-size:14px;overflow-wrap:anywhere;word-break:break-word}
                        table{width:100%;border-collapse:collapse;background:var(--panel);border:1px solid var(--line);table-layout:fixed}
                        th,td{border:1px solid var(--line);padding:9px 10px;text-align:left;vertical-align:top;overflow-wrap:anywhere;word-break:break-word;white-space:normal}
                        th{background:#eef5ff;font-weight:650}
                        .table-wrap{width:100%;overflow-x:auto}
                        .summary p{background:var(--panel);border:1px solid var(--line);border-radius:8px;margin:8px 0;padding:10px 12px;overflow-wrap:anywhere}
                        .note{color:var(--muted);font-size:13px}
                        .pill{display:inline-block;border-radius:999px;border:1px solid var(--line);padding:2px 8px;font-size:12px;background:#f8fafc}
                        @media print{body{background:#fff}.page{max-width:none;padding:12px}.kv,.summary p,table{break-inside:avoid}}
                        """)
                .append("</style></head><body><main class=\"page\">");

        html.append("<section class=\"top\"><h1>API-HUB Monitor Report Workbench</h1>")
                .append("<div class=\"status ").append(escape(colorLevel)).append("\">")
                .append(escape(value(status.get("statusLabel"), statusCode))).append("</div>")
                .append("<div class=\"subtitle\">")
                .append(escape(value(status.get("statusSummary"), "基于已落库事实生成的 API 运行分析报告。")))
                .append("</div></section>");

        sectionHeader(html, "报告头部");
        kvGrid(html, header, List.of(
                "reportCode", "reportTypeLabel", "generatedAt", "analysisTarget",
                "timeRange", "modelName", "dataSources"
        ), Map.of(
                "reportCode", "报告编号",
                "reportTypeLabel", "报告类型",
                "generatedAt", "生成时间",
                "analysisTarget", "分析对象",
                "timeRange", "时间范围",
                "modelName", "模型名称",
                "dataSources", "数据来源"
        ));

        sectionHeader(html, "状态概览");
        kvGrid(html, status, List.of("status", "statusLabel", "colorLevel", "statusSummary"), Map.of(
                "status", "状态",
                "statusLabel", "展示文案",
                "colorLevel", "颜色等级",
                "statusSummary", "状态摘要"
        ));

        sectionHeader(html, "体检式核心指标");
        table(html, list(htmlJson.get("metricCheckup")),
                List.of("metricName", "currentWindowValue", "referenceWindowValue", "changeValue", "displayStatus", "evidenceIds"),
                List.of("指标", "当前区间", "对照区间", "变化值", "状态", "证据"));

        sectionHeader(html, "监测规则判定");
        table(html, list(htmlJson.get("monitorRuleAssessments")),
                List.of("ruleDisplayName", "metricName", "currentValue", "thresholdValue", "deviationValue", "assessmentLabel", "evidenceIds"),
                List.of("监测规则", "指标", "当前值", "判定阈值", "偏差", "监测结论", "证据"));

        sectionHeader(html, "业务码分布");
        table(html, list(htmlJson.get("businessCodeDistribution")),
                List.of("businessCodeLabel", "description", "count", "ratio", "displayStatus", "evidenceIds"),
                List.of("业务码", "说明", "数量", "占比", "状态", "证据"));

        sectionHeader(html, "事件时间线");
        table(html, list(htmlJson.get("eventTimeline")),
                List.of("absoluteTime", "relativeTime", "phase", "description"),
                List.of("绝对时间", "相对时间", "阶段", "说明"));

        sectionHeader(html, "诊断摘要");
        html.append("<div class=\"summary\">");
        for (Object item : list(htmlJson.get("diagnosisSummary"))) {
            html.append("<p>").append(escape(item)).append("</p>");
        }
        html.append("</div>");

        sectionHeader(html, "建议操作");
        table(html, list(htmlJson.get("operationRecommendations")),
                List.of("priority", "basisMetricOrEvidence", "operationRecommendation", "evidenceIds"),
                List.of("优先级", "依据指标 / 证据", "建议操作", "关联证据"));

        sectionHeader(html, "证据明细");
        table(html, list(htmlJson.get("evidenceList")),
                List.of("evidenceId", "evidenceTypeLabel", "source", "keyMetric", "deviationValue", "relatedConclusion"),
                List.of("Evidence ID", "类型", "来源", "关键指标 / 差异", "偏差", "关联结论"));

        sectionHeader(html, "数据说明");
        html.append("<p class=\"note\">").append(escape(value(htmlJson.get("dataBoundaryNote"),
                "说明：本报告基于本地模拟场景与开发环境数据生成，不代表真实线上用户影响。"))).append("</p>");

        html.append("</main></body></html>");
        return html.toString();
    }

    private Map<String, Object> fallbackJson(Map<String, Object> report) {
        Map<String, Object> json = new LinkedHashMap<>();
        String riskLevel = value(report.get("riskLevel"), "UNKNOWN");
        String displayStatus = normalizeStatus(riskLevel);
        json.put("reportType", value(report.get("reportType"), "INCIDENT_ANALYSIS"));
        json.put("reportHeader", Map.of(
                "reportCode", value(report.get("reportCode"), "RPT-" + report.get("reportId")),
                "reportTypeLabel", value(report.get("reportType"), "INCIDENT_ANALYSIS"),
                "generatedAt", FORMATTER.format(LocalDateTime.now()),
                "analysisTarget", value(report.get("apiCode"), "UNKNOWN"),
                "timeRange", "",
                "modelName", "deterministic-only",
                "dataSources", List.of("agent_report", "evidence_item")
        ));
        json.put("displayStatus", Map.of(
                "status", displayStatus,
                "statusLabel", statusLabel(displayStatus),
                "colorLevel", colorLevel(displayStatus),
                "statusSummary", value(report.get("summary"), "No summary.")
        ));
        json.put("metricCheckup", List.of());
        json.put("monitorRuleAssessments", List.of());
        json.put("businessCodeDistribution", List.of());
        json.put("eventTimeline", List.of());
        json.put("diagnosisSummary", List.of(value(report.get("summary"), "No summary.")));
        json.put("operationRecommendations", List.of());
        json.put("evidenceList", List.of());
        json.put("dataBoundaryNote", "说明：本报告基于本地模拟场景与开发环境数据生成，不代表真实线上用户影响。");
        return json;
    }

    private void sectionHeader(StringBuilder html, String title) {
        html.append("<h2>").append(escape(title)).append("</h2>");
    }

    private void kvGrid(StringBuilder html, Map<String, Object> data, List<String> keys, Map<String, String> labels) {
        html.append("<div class=\"grid\">");
        for (String key : keys) {
            html.append("<div class=\"kv\"><div class=\"k\">").append(escape(labels.getOrDefault(key, key)))
                    .append("</div><div class=\"v\">").append(escapeValue(data.get(key))).append("</div></div>");
        }
        html.append("</div>");
    }

    private void table(StringBuilder html, List<Object> rows, List<String> keys, List<String> labels) {
        html.append("<div class=\"table-wrap\"><table><thead><tr>");
        for (String label : labels) {
            html.append("<th>").append(escape(label)).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        if (rows == null || rows.isEmpty()) {
            html.append("<tr><td colspan=\"").append(labels.size()).append("\"><span class=\"note\">无记录</span></td></tr>");
        } else {
            for (Object item : rows) {
                Map<String, Object> row = map(item);
                html.append("<tr>");
                for (String key : keys) {
                    html.append("<td>").append(escapeValue(row.get(key))).append("</td>");
                }
                html.append("</tr>");
            }
        }
        html.append("</tbody></table></div>");
    }

    private String escapeValue(Object value) {
        if (value instanceof Collection<?> collection) {
            StringBuilder joined = new StringBuilder();
            boolean first = true;
            for (Object item : collection) {
                if (!first) {
                    joined.append(", ");
                }
                first = false;
                joined.append(value(item, ""));
            }
            return escape(joined.toString());
        }
        return escape(value);
    }

    private Map<String, Object> parseMap(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return objectMapper.readValue(text, MAP_TYPE);
            } catch (Exception ignored) {
                return new LinkedHashMap<>();
            }
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Object> map(Object value) {
        return parseMap(value);
    }

    private List<Object> list(Object value) {
        if (value instanceof List<?> list) {
            return new java.util.ArrayList<>(list);
        }
        if (value instanceof Collection<?> collection) {
            return new java.util.ArrayList<>(collection);
        }
        return List.of();
    }

    private String normalizeStatus(String status) {
        if ("NORMAL".equals(status) || "WATCH".equals(status) || "WARNING".equals(status) || "UNKNOWN".equals(status)) {
            return status;
        }
        if ("CRITICAL".equals(status)) {
            return "WARNING";
        }
        return "UNKNOWN";
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "NORMAL" -> "正常（NORMAL）";
            case "WATCH" -> "关注（WATCH）";
            case "WARNING" -> "警告（WARNING）";
            default -> "未知（UNKNOWN）";
        };
    }

    private String colorLevel(String status) {
        return switch (status) {
            case "NORMAL" -> "GREEN";
            case "WATCH" -> "BLUE";
            case "WARNING" -> "YELLOW";
            default -> "GRAY";
        };
    }

    private String value(Object value, String fallback) {
        String text = value == null ? null : String.valueOf(value);
        return StringUtils.hasText(text) ? text : fallback;
    }

    private String escape(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
