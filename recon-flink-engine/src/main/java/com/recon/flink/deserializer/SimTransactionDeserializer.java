package com.recon.flink.deserializer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.flink.domain.FlatLineItem;
import com.recon.flink.domain.FlatSimTransaction;
import com.recon.poller.domain.SimTransactionEvent;
import com.recon.poller.domain.SiocsLineItem;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import java.util.List;

public class SimTransactionDeserializer
        implements DeserializationSchema<FlatSimTransaction> {

    private static final long serialVersionUID = 1L;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    false);

    @Override
    public FlatSimTransaction deserialize(byte[] message) {
        if (message == null || message.length == 0) return null;
        try {
            SimTransactionEvent src = MAPPER.readValue(
                    message, SimTransactionEvent.class);
            return toFlat(src);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to deserialize SimTransactionEvent", e);
        }
    }

    private FlatSimTransaction toFlat(SimTransactionEvent src) {
        FlatSimTransaction flat = new FlatSimTransaction();
        flat.setTransactionKey(src.getTransactionKey());
        flat.setExternalId(src.getExternalId());
        flat.setSource(src.getSource());
        flat.setStoreId(src.getStoreId());
        flat.setBusinessDate(src.getBusinessDate());
        flat.setRequestId(src.getRequestId());
        flat.setTransactionDateTime(src.getTransactionDateTime());
        flat.setUpdateDateTime(src.getUpdateDateTime());
        flat.setTransactionType(src.getTransactionType());
        flat.setTransactionTypeDesc(src.getTransactionTypeDesc());
        flat.setProcessingStatus(src.getProcessingStatus());
        flat.setProcessingStatusDesc(src.getProcessingStatusDesc());
        flat.setLineItemCount(src.getLineItemCount());
        flat.setTotalQuantity(src.getTotalQuantity());
        flat.setDuplicateFlag(src.isDuplicateFlag());
        flat.setDuplicatePostingCount(src.getDuplicatePostingCount());
        flat.setChecksum(src.getChecksum());

        // Derive wkstnId from externalId
        // format: 0{4-storeId}{3-wkstn}{6-seq}{8-date} = 22 chars
        String eid = src.getExternalId();
        if (eid != null && eid.length() == 22) {
            try {
                flat.setWkstnId(String.valueOf(
                        Integer.parseInt(eid.substring(5, 8))));
            } catch (Exception ignored) {
            }
        }

        // Convert List<SiocsLineItem> to FlatLineItem[]
        List<SiocsLineItem> items = src.getLineItems();
        if (items != null && !items.isEmpty()) {
            FlatLineItem[] flatItems = new FlatLineItem[items.size()];
            for (int i = 0; i < items.size(); i++) {
                SiocsLineItem li = items.get(i);
                FlatLineItem fli = new FlatLineItem();
                fli.setItemId(li.getItemId());
                fli.setQuantity(li.getQuantity());
                fli.setUnitOfMeasure(li.getUnitOfMeasure());
                fli.setLineType(resolveLineType(li.getType()));
                fli.setLineSeq(i + 1);
                flatItems[i] = fli;
            }
            flat.setLineItems(flatItems);
        } else {
            flat.setLineItems(new FlatLineItem[0]);
        }
        return flat;
    }

    private String resolveLineType(Integer type) {
        return switch (type != null ? type : -1) {
            case 1 -> "Sale";
            case 2 -> "Return";
            case 3 -> "VoidSale";
            case 4 -> "VoidReturn";
            case 10, 11, 12 -> "Transfer";
            case 20, 21, 22 -> "Receiving";
            case 30 -> "DirectStoreDelivery";
            case 40 -> "InventoryAdjustment";
            case 50 -> "PurchaseOrder";
            case 60 -> "ReturnToVendor";
            case 70 -> "WarehouseDelivery";
            case 80 -> "StoreTransfer";
            default -> "Unknown";
        };
    }

    @Override
    public boolean isEndOfStream(FlatSimTransaction nextElement) {
        return false;
    }

    @Override
    public TypeInformation<FlatSimTransaction> getProducedType() {
        return TypeInformation.of(FlatSimTransaction.class);
    }
}
