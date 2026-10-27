package com.ahvakra.camelintegration.tables.domain;

import java.util.List;

public record SchemaChangeSelection(
    String fingerprint,
    List<String> changeIds,
    boolean acknowledgeDataLoss
) {}
