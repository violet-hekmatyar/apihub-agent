package com.apihub.agent.service;

import com.apihub.agent.model.vo.DbHealthVO;
import com.apihub.agent.model.vo.HealthVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class HealthService {

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;

    public HealthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public HealthVO getHealth() {
        return new HealthVO(
                "apihub-agent-server",
                "UP",
                FORMATTER.format(LocalDateTime.now()),
                Runtime.version().feature() + ""
        );
    }

    public DbHealthVO getDbHealth() {
        DbHealthVO result = new DbHealthVO();
        try {
            result.setSelectOne(jdbcTemplate.queryForObject("SELECT 1", Integer.class));
            result.setDatabaseName(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));
            result.setTableCount(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()",
                    Integer.class
            ));
            result.setKeyTableRows(queryKeyTableRows());
            result.setStatus("UP");
        } catch (Exception e) {
            log.warn("database health check failed: {}", e.getMessage());
            result.setStatus("DOWN");
            result.setErrorMessage("database health check failed");
        }
        return result;
    }

    private Map<String, Long> queryKeyTableRows() {
        Map<String, Long> rows = new LinkedHashMap<>();
        rows.put("sys_user", countTable("sys_user"));
        rows.put("api_endpoint", countTable("api_endpoint"));
        rows.put("api_call_stat_hourly", countTable("api_call_stat_hourly"));
        rows.put("gateway_log", countTable("gateway_log"));
        rows.put("agent_session", countTable("agent_session"));
        return rows;
    }

    private Long countTable(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
    }
}
