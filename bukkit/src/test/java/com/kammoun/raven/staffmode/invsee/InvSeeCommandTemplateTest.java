package com.kammoun.raven.staffmode.invsee;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvSeeCommandTemplateTest {

    @Test
    void buildsCleanPlayerCommandWithoutLeadingSlash() {
        assertEquals("inventory zYousef",
                InvSeeCommandTemplate.parse("/inventory %player%", "zYousef"));
    }

    @Test
    void supportsEditPlaceholderWithoutSplittingActionsInMenus() {
        assertEquals("inventory zYousef edit",
                InvSeeCommandTemplate.parse("inventory %player% %mode%", "zYousef", true));
        assertEquals("inventory zYousef",
                InvSeeCommandTemplate.parse("inventory %player% %mode%", "zYousef", false));
    }
}
