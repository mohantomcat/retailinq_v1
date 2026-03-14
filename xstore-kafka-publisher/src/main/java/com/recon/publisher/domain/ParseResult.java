package com.recon.publisher.domain;

import lombok.Getter;

@Getter
public class ParseResult {

    private final boolean success;
    private final boolean skipped;
    private final boolean failed;
    private final PosTransactionEvent event;
    private final String transactionType;
    private final String errorMessage;
    private final boolean compressed;

    private ParseResult(boolean success, boolean skipped, boolean failed,
                        PosTransactionEvent event, String transactionType,
                        String errorMessage, boolean compressed) {
        this.success = success;
        this.skipped = skipped;
        this.failed = failed;
        this.event = event;
        this.transactionType = transactionType;
        this.errorMessage = errorMessage;
        this.compressed = compressed;
    }

    public static ParseResult success(PosTransactionEvent event,
                                      boolean compressed) {
        return new ParseResult(true, false, false, event,
                event.getTransactionType(), null, compressed);
    }

    public static ParseResult skipped(String transactionType) {
        return new ParseResult(false, true, false, null,
                transactionType, null, false);
    }

    public static ParseResult failed(String errorMessage) {
        return new ParseResult(false, false, true, null,
                null, errorMessage, false);
    }
}