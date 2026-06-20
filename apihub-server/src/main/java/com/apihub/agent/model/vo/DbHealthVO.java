package com.apihub.agent.model.vo;

import java.util.Map;

public class DbHealthVO {

    private String status;
    private Integer selectOne;
    private String databaseName;
    private Integer tableCount;
    private Map<String, Long> keyTableRows;
    private String errorMessage;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getSelectOne() {
        return selectOne;
    }

    public void setSelectOne(Integer selectOne) {
        this.selectOne = selectOne;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public Integer getTableCount() {
        return tableCount;
    }

    public void setTableCount(Integer tableCount) {
        this.tableCount = tableCount;
    }

    public Map<String, Long> getKeyTableRows() {
        return keyTableRows;
    }

    public void setKeyTableRows(Map<String, Long> keyTableRows) {
        this.keyTableRows = keyTableRows;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
