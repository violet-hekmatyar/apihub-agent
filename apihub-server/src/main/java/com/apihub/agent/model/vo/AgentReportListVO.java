package com.apihub.agent.model.vo;

import java.util.ArrayList;
import java.util.List;

public class AgentReportListVO {

    private int pageNo;
    private int pageSize;
    private long total;
    private List<AgentReportListItemVO> items = new ArrayList<>();

    public int getPageNo() {
        return pageNo;
    }

    public void setPageNo(int pageNo) {
        this.pageNo = pageNo;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<AgentReportListItemVO> getItems() {
        return items;
    }

    public void setItems(List<AgentReportListItemVO> items) {
        this.items = items;
    }
}
