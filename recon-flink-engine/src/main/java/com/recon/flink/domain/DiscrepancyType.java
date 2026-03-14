package com.recon.flink.domain;

public enum DiscrepancyType {
    ITEM_MISSING,        // item in Xstore but not in SIOCS
    ITEM_EXTRA,          // item in SIOCS but not in Xstore
    QUANTITY_MISMATCH,   // item present both sides, qty differs
    UOM_MISMATCH         // item present both sides, uom differs
}