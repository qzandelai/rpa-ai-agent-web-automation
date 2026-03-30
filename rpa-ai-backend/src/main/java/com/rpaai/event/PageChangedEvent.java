package com.rpaai.event;

import lombok.Getter;

@Getter
public class PageChangedEvent extends RpaEvent {
    private final String url;
    private final String title;
    private final String browserSessionId;

    public PageChangedEvent(Object source, String browserSessionId, String url, String title) {
        super(source, null, null); // 页面变化可能不关联特定任务
        this.browserSessionId = browserSessionId;
        this.url = url;
        this.title = title;
    }
}