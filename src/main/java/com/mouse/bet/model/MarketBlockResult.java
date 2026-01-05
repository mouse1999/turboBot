package com.mouse.bet.model;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

public class MarketBlockResult {
    public final Locator block;
    public final String markerId;
    public final String title;

    public MarketBlockResult(Locator block, String markerId, String title) {
        this.block = block;
        this.markerId = markerId;
        this.title = title;
    }

    // Refresh if needed
    public Locator refresh(Page page) {
        return page.locator("[data-pw-market-marker='" + markerId + "']").first();
    }
}
