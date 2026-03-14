package com.recon.publisher.util;

import java.math.BigDecimal;

public interface ReconcilableItem {
    String getItemId();

    BigDecimal getQuantity();

    String getUnitOfMeasure();

    String getLineType();
}
