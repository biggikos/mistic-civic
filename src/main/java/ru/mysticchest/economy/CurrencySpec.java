package ru.mysticchest.economy;

/**
 * A tier "currency" value: {@code [provider:]currency}.
 * "gold" -> default provider, currency "gold"; "excellenteconomy:coins" -> that provider;
 * "playerpoints" or "vault" alone -> that provider with no currency name.
 */
public final class CurrencySpec {
    public static final String[] PROVIDERS = {"vault", "excellenteconomy", "coinsengine", "playerpoints"};

    public final String provider;   // null = use the default provider
    public final String currency;   // may be empty
    public final String display;

    private CurrencySpec(String provider, String currency, String display) {
        this.provider = provider;
        this.currency = currency;
        this.display = display;
    }

    public static String canonical(String token) {
        String t = token.trim().toLowerCase().replace("_", "").replace("-", "");
        if (t.equals("ee")) return "excellenteconomy";
        if (t.equals("ce")) return "coinsengine";
        if (t.equals("pp") || t.equals("points")) return "playerpoints";
        for (String p : PROVIDERS) if (p.equals(t)) return p;
        return null;
    }

    public static CurrencySpec parse(String raw) {
        String s = raw == null ? "" : raw.trim();
        int i = s.indexOf(':');
        if (i > 0) {
            String prov = canonical(s.substring(0, i));
            if (prov != null) {
                String cur = s.substring(i + 1).trim();
                return new CurrencySpec(prov, cur, cur.isEmpty() ? prov : cur);
            }
        }
        String prov = canonical(s);
        if (prov != null) return new CurrencySpec(prov, "", prov);
        return new CurrencySpec(null, s, s);
    }
}
