package com.example.salesmgmt;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Protect presentation-only changes without requiring a database migration.
 * The same stylesheet is deliberately used by every Thymeleaf page.
 */
class UiStyleConsistencyTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/resources",path), StandardCharsets.UTF_8);
    }

    @Test void allHtmlPagesUseOneCurrentSharedDesignSystem() throws Exception {
        try(Stream<Path> pages=Files.walk(Path.of("src/main/resources/templates"))) {
            var htmlPages=pages.filter(path->path.toString().endsWith(".html")).toList();
            assertThat(htmlPages).hasSizeGreaterThan(30);
            for(Path page:htmlPages){
                String contents=Files.readString(page,StandardCharsets.UTF_8);
                assertThat(contents).as("Shared UI stylesheet missing: "+page)
                        .contains("songcheon-ui.css(v=20261125_polish1)");
                assertThat(contents).as("Stale CSS cache version: "+page)
                        .doesNotContain("songcheon-ui.css(v=20261125_flat)");
            }
        }
    }

    @Test void profitUsesRealTabsInsteadOfInconsistentLinkButtons() throws Exception {
        String profit=source("templates/profit.html");
        String style=source("static/css/profit.css");
        assertThat(profit).contains("<nav class=\"sc-work-tabs\"")
                .contains("aria-current=\"page\"")
                .contains("수익 분석","콩 재고 관리")
                .doesNotContain("class=\"primary-link\" th:href=\"@{/profit");
        assertThat(style).contains(".sc-work-tabs", ".sc-work-tab.active");
    }

    @Test void vendorDirectoryKeepsSearchAndLinksButRemovesFloatingCardGrid() throws Exception {
        String page=source("templates/vendor-management.html");
        String style=source("static/css/vendor-management-v2.css");
        assertThat(page).contains("id=\"vendorSearch\"","id=\"initialFilter\"",
                "id=\"vendorList\"","th:data-name=\"\u0024{vendor.inputName}\"",
                "vendor-list-head","vendor-detail-link");
        assertThat(style).contains(".vendor-list{display:block")
                .doesNotContain(".vendor-list{display:grid;grid-template-columns:repeat(2");
    }

    @Test void sharedStylesLoadKoreanFontAndSidebarRemainsCollapsible() throws Exception {
        String css=source("static/css/songcheon-ui.css");
        String script=source("static/js/global-sidebar-and-recovery.js");
        assertThat(css).contains("pretendard.css","a.primary-link","--ui-control-height",
                "box-shadow: none", "songcheon-sidebar-collapsed");
        assertThat(script).contains("songcheon.sidebar.collapsed",
                "localStorage.setItem(navStateKey","songcheon-sidebar-toggle",
                "aria-expanded");
    }
}
