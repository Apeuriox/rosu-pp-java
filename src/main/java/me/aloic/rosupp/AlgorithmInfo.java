package me.aloic.rosupp;

public record AlgorithmInfo(
        AlgorithmVersion algorithm,
        String name,
        String version,
        String details,
        AlgorithmCapabilities capabilities) {}
