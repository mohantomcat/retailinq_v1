package com.recon.cloud.util;

import java.math.BigDecimal;

public interface ReconcilableItem {
    String getItemId();

    BigDecimal getQuantity();

    String getUnitOfMeasure();

    String getLineType();
}
