package ru.itam.typing;

import org.junit.jupiter.api.Test;
import ru.itam.typing.engine.common.MatchResolver;
import ru.itam.typing.model.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchResolverTest {
    @Test
    void highestPriorityWins() {
        var result = MatchResolver.resolve("a", List.of(
                new RuleMatch("LOW", AssetType.DEVICE, null, 10),
                new RuleMatch("HIGH", AssetType.DEVICE, AssetSubtype.SERVER, 20)));
        assertEquals(TypingStatus.AUTO, result.status());
        assertEquals(AssetType.DEVICE, result.type());
        assertEquals(AssetSubtype.SERVER, result.subtype());
        assertEquals(List.of("HIGH"), result.matchedRuleIds());
    }

    @Test
    void equalPriorityDifferentTypesIsConflict() {
        var result = MatchResolver.resolve("a", List.of(
                new RuleMatch("A", AssetType.DEVICE, null, 100),
                new RuleMatch("B", AssetType.ACCOUNT, null, 100)));
        assertEquals(TypingStatus.TYPE_CONFLICT, result.status());
        assertNull(result.type());
    }

    @Test
    void equalPriorityDifferentSubtypesIsConflict() {
        var result = MatchResolver.resolve("a", List.of(
                new RuleMatch("A", AssetType.DEVICE, AssetSubtype.SERVER, 100),
                new RuleMatch("B", AssetType.DEVICE, AssetSubtype.WORKSTATION, 100)));
        assertEquals(TypingStatus.SUBTYPE_CONFLICT, result.status());
        assertEquals(AssetType.DEVICE, result.type());
        assertNull(result.subtype());
    }

    @Test
    void duplicateExpandedDmnRowsAreDeduplicatedByRuleId() {
        var result = MatchResolver.resolve("a", List.of(
                new RuleMatch("A", AssetType.DEVICE, AssetSubtype.SERVER, 100),
                new RuleMatch("A", AssetType.DEVICE, AssetSubtype.SERVER, 100)));
        assertEquals(TypingStatus.AUTO, result.status());
        assertEquals(List.of("A"), result.matchedRuleIds());
    }
}
