package com.ahvakra.camelintegration.rules.domain;

import java.time.Instant;
import java.util.UUID;

public record NormalizeRule(UUID id, NormalizeSpec spec, Instant createdAt, Instant updatedAt) {}
