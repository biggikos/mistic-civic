package ru.mysticchest;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import org.junit.jupiter.api.Test;
import ru.mysticchest.util.Chat;

import static org.junit.jupiter.api.Assertions.*;

class ChatTest {
    private static ClickEvent clickOf(BaseComponent[] cs, String text) {
        for (BaseComponent c : cs) if (c.toPlainText().contains(text)) return c.getClickEvent();
        return null;
    }

    @Test
    void buttonBecomesClickableComponent() {
        BaseComponent[] cs = Chat.parse("&7before [[&a[Track]|run:/mystic track 7|&7hover]] after");
        ClickEvent e = clickOf(cs, "Track");
        assertNotNull(e);
        assertEquals(ClickEvent.Action.RUN_COMMAND, e.getAction());
        assertEquals("/mystic track 7", e.getValue());
        assertNull(clickOf(cs, "before"));
    }

    @Test
    void supportsSuggestUrlAndCopy() {
        assertEquals(ClickEvent.Action.SUGGEST_COMMAND, clickOf(Chat.parse("[[x|suggest:/a b]]"), "x").getAction());
        assertEquals(ClickEvent.Action.OPEN_URL, clickOf(Chat.parse("[[x|url:https://example.com]]"), "x").getAction());
        ClickEvent copy = clickOf(Chat.parse("[[x|copy:1 2 3]]"), "x");
        assertEquals("1 2 3", copy.getValue());
    }

    @Test
    void plainLinesHaveNoButtons() {
        assertFalse(Chat.hasButtons("just text [with] brackets"));
        assertTrue(Chat.hasButtons("a [[b|run:/c]] d"));
        assertEquals(1, Chat.parse("hello").length > 0 ? 1 : 0);
    }
}
