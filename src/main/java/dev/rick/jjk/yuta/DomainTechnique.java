package dev.rick.jjk.yuta;

/**
 * The techniques Authentic Mutual Love's katanas can carry (JJS), each with the colour its blade glows (its light column,
 * ground ring and name), picked so no two can be confused: Shrine's crimson, Thin Ice Breaker's ice blue, Clairvoyance's
 * manga-panel gold, Cursed Speech's violet, the Shikigami's Rika-white.
 */
public enum DomainTechnique {
    SHRINE("Shrine", 0xFFFF3B47),
    THIN_ICE_BREAKER("Thin Ice Breaker", 0xFF8FE3FF),
    CLAIRVOYANCE("Clairvoyance", 0xFFFFC53A),
    CURSED_SPEECH("Cursed Speech", 0xFFA66BFF),
    SHIKIGAMI("Shikigami", 0xFFF4F4F4);

    public final String title;
    public final int color;

    DomainTechnique(String title, int color) {
        this.title = title;
        this.color = color;
    }
}
