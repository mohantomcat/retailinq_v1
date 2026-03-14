package com.recon.flink.util;

import com.recon.poller.domain.SiocsLineItem;
import com.recon.publisher.domain.LineItem;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ChecksumUtil {

    public static String computeFromLineItems(List<LineItem> items) {
        if (items == null || items.isEmpty()) {
            return DigestUtils.sha256Hex("");
        }
        String canonical = items.stream()
                .filter(i -> i.getItemId() != null
                        && i.getQuantity() != null)
                .sorted(Comparator.comparing(LineItem::getItemId))
                .map(i ->
                        i.getItemId() + ":" +
                                i.getQuantity().toPlainString() + ":" +
                                Objects.toString(i.getUnitOfMeasure(), ""))
                .collect(Collectors.joining("|"));
        return DigestUtils.sha256Hex(canonical);
    }

    public static String computeFromSiocsItems(List<SiocsLineItem> items) {
        if (items == null || items.isEmpty()) {
            return DigestUtils.sha256Hex("");
        }
        String canonical = items.stream()
                .filter(i -> i.getItemId() != null
                        && i.getQuantity() != null)
                .sorted(Comparator.comparing(SiocsLineItem::getItemId))
                .map(i ->
                        i.getItemId() + ":" +
                                i.getQuantity().toPlainString() + ":" +
                                Objects.toString(i.getUnitOfMeasure(), ""))
                .collect(Collectors.joining("|"));
        return DigestUtils.sha256Hex(canonical);
    }
}