package me.aloic.rosupp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DifficultyRequestTest {
    @Test
    void structuredModsAreExposedWithoutReplacingLegacyMods() {
        var json = "[{\"acronym\":\"HD\"}]";
        var request = DifficultyRequest.builder().modsJson(json).build();

        assertEquals(json, request.modsJson().orElseThrow());
        assertEquals(Mods.NONE, request.mods());
        assertThrows(IllegalStateException.class,
                () -> DifficultyRequest.builder().modsJson(json).mods(Mods.HIDDEN));
        assertThrows(IllegalStateException.class,
                () -> DifficultyRequest.builder().mods(Mods.HIDDEN).modsJson(json));
        assertThrows(IllegalArgumentException.class,
                () -> DifficultyRequest.builder().modsJson("  "));
    }
}
