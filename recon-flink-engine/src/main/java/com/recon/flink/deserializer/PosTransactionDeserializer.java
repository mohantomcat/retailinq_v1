package com.recon.flink.deserializer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.flink.domain.FlatLineItem;
import com.recon.flink.domain.FlatPosTransaction;
import com.recon.publisher.domain.LineItem;
import com.recon.publisher.domain.PosTransactionEvent;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import java.util.List;

public class PosTransactionDeserializer
        implements DeserializationSchema<FlatPosTransaction> {

    private static final long serialVersionUID = 1L;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    false);

    @Override
    public FlatPosTransaction deserialize(byte[] message) {
        if (message == null || message.length == 0) return null;
        try {
            // Deserialize via domain object (handles all Jackson annotations)
            PosTransactionEvent src = MAPPER.readValue(
                    message, PosTransactionEvent.class);
            return toFlat(src);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to deserialize PosTransactionEvent", e);
        }
    }

    private FlatPosTransaction toFlat(PosTransactionEvent src) {
        FlatPosTransaction flat = new FlatPosTransaction();
        flat.setTransactionKey(src.getTransactionKey());
        flat.setExternalId(src.getExternalId());
        flat.setOrganizationId(src.getOrganizationId());
        flat.setStoreId(src.getStoreId());
        flat.setBusinessDate(src.getBusinessDate());
        flat.setWkstnId(src.getWkstnId());
        flat.setTransSeq(src.getTransSeq());
        flat.setTransactionType(src.getTransactionType());
        flat.setBeginDatetime(src.getBeginDatetime());
        flat.setEndDatetime(src.getEndDatetime());
        flat.setOperatorId(src.getOperatorId());
        flat.setTotalAmount(src.getTotalAmount());
        flat.setChecksum(src.getChecksum());
        flat.setCompressed(src.isCompressed());
        flat.setClockDriftDetected(src.isClockDriftDetected());

        // Convert List<LineItem> to FlatLineItem[]
        List<LineItem> items = src.getLineItems();
        if (items != null && !items.isEmpty()) {
            FlatLineItem[] flatItems = new FlatLineItem[items.size()];
            for (int i = 0; i < items.size(); i++) {
                LineItem li = items.get(i);
                FlatLineItem fli = new FlatLineItem();
                fli.setItemId(li.getItemId());
                fli.setQuantity(li.getQuantity());
                fli.setUnitOfMeasure(li.getUnitOfMeasure());
                fli.setUnitPrice(li.getUnitPrice());
                fli.setExtendedAmount(li.getExtendedAmount());
                fli.setLineType(li.getLineType());
                fli.setLineSeq(li.getLineSeq());
                flatItems[i] = fli;
            }
            flat.setLineItems(flatItems);
        } else {
            flat.setLineItems(new FlatLineItem[0]);
        }
        return flat;
    }

    @Override
    public boolean isEndOfStream(FlatPosTransaction nextElement) {
        return false;
    }

    @Override
    public TypeInformation<FlatPosTransaction> getProducedType() {
        return TypeInformation.of(FlatPosTransaction.class);
    }
}