package ru.mysticchest.util;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Clickable chat. A language line may contain buttons written as
 *   [[label|action|hover text]]
 * where action is  run:/command   suggest:/command   copy:text   url:https://...  (hover is optional, "\n" = new line).
 * Colour codes (&a, &#rrggbb) work everywhere. Lines without buttons are sent as plain text.
 */
public final class Chat {
    private Chat() {}

    public static boolean hasButtons(String s) { return s.contains("[[") && s.contains("]]"); }

    private static BaseComponent[] legacy(String s) { return TextComponent.fromLegacyText(Text.color(s)); }

    public static BaseComponent[] parse(String line) {
        List<BaseComponent> out = new ArrayList<BaseComponent>();
        int i = 0;
        while (i < line.length()) {
            int a = line.indexOf("[[", i);
            int b = a < 0 ? -1 : line.indexOf("]]", a + 2);
            if (a < 0 || b < 0) { addAll(out, legacy(line.substring(i))); break; }
            if (a > i) addAll(out, legacy(line.substring(i, a)));
            String[] p = line.substring(a + 2, b).split("\\|", 3);
            BaseComponent[] label = legacy(p[0]);
            if (p.length > 1) {
                ClickEvent click = click(p[1].trim());
                HoverEvent hover = p.length > 2 && !p[2].isEmpty() ? new HoverEvent(HoverEvent.Action.SHOW_TEXT, legacy(p[2].replace("\\n", "\n"))) : null;
                for (BaseComponent c : label) {
                    if (click != null) c.setClickEvent(click);
                    if (hover != null) c.setHoverEvent(hover);
                }
            }
            addAll(out, label);
            i = b + 2;
        }
        return out.toArray(new BaseComponent[0]);
    }

    private static void addAll(List<BaseComponent> out, BaseComponent[] arr) { for (BaseComponent c : arr) out.add(c); }

    private static ClickEvent click(String a) {
        int c = a.indexOf(':');
        if (c < 0) return null;
        String kind = a.substring(0, c).toLowerCase(), val = a.substring(c + 1);
        try {
            if (kind.equals("run")) return new ClickEvent(ClickEvent.Action.RUN_COMMAND, val);
            if (kind.equals("suggest")) return new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, val);
            if (kind.equals("url")) return new ClickEvent(ClickEvent.Action.OPEN_URL, val);
            if (kind.equals("copy")) {
                try { return new ClickEvent(ClickEvent.Action.valueOf("COPY_TO_CLIPBOARD"), val); }
                catch (IllegalArgumentException old) { return new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, val); }   // < 1.15: paste into the chat box
            }
        } catch (Throwable t) { /* odd server: no button */ }
        return null;
    }

    public static void send(Player p, String line) {
        if (!hasButtons(line)) { p.sendMessage(line); return; }
        try {
            p.spigot().sendMessage(parse(line));
        } catch (Throwable t) {
            p.sendMessage(line.replaceAll("\\[\\[([^|\\]]*)[^\\]]*\\]\\]", "$1"));
        }
    }
}
