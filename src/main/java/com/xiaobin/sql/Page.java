package com.xiaobin.sql;

import java.util.List;

/**
 * 分页类
 * @param <T>
 */
public class Page<T> {

    private int start;//起始记录
    private int end;//结束记录
    private long total;//总条数
    private List<T> list;//记录

    public int getStart() {
        return start;
    }

    public void setStart(int start) {
        this.start = Math.max(start, 0);
    }

    public int getEnd() {
        return end;
    }

    public void setEnd(int end) {
        this.end = end;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }
}
