package com.apihub.agent.common;

import java.util.List;

public class PageResponse<T> {

    private List<T> items;
    private long total;
    private int pageNo;
    private int pageSize;

    public PageResponse() {
    }

    public PageResponse(List<T> items, long total, int pageNo, int pageSize) {
        this.items = items;
        this.total = total;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

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
}
