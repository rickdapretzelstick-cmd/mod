package dev.rick.jjk.core.domain;

/** An ability that opens a domain. Lets generic systems (counters, cinematics) find a character's domain. */
public interface DomainAbility {
    DomainDefinition domain();
}
