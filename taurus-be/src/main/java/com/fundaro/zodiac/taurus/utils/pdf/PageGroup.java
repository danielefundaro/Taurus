package com.fundaro.zodiac.taurus.utils.pdf;

public class PageGroup {

    private final int firstPage;
    private final int lastPage;
    private final String title;

    public PageGroup(int firstPage, int lastPage, String title) {
        this.firstPage = firstPage;
        this.lastPage = lastPage;
        this.title = title;
    }

    public int getFirstPage() {
        return firstPage;
    }

    public int getLastPage() {
        return lastPage;
    }

    public String getTitle() {
        return title;
    }

    public int getPageCount() {
        return lastPage - firstPage + 1;
    }
}
