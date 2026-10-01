package dev.rick.jjk.yuta;

/** The techniques Authentic Mutual Love's katanas can carry (JJS), each with its tint for the blade and the HUD. */
public enum DomainTechnique {
    SHRINE("Shrine", 0xFFE0E0E0),
    THIN_ICE_BREAKER("Thin Ice Breaker", 0xFF9EE6FF),
    CLAIRVOYANCE("Clairvoyance", 0xFFB0303A),
    CURSED_SPEECH("Cursed Speech", 0xFF101014),
    SHIKIGAMI("Shikigami", 0xFFF2F2F2);

    public final String title;
    public final int color;

    DomainTechnique(String title, int color) {
        this.title = title;
        this.color = color;
    }
}
