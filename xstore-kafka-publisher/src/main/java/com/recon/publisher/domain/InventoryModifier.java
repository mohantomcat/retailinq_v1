package com.recon.publisher.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryModifier {
    private String actionCode;
    private String sourceBucketId;
    private String destinationBucketId;
    private String key; // actionCode:sourceBucketId:destinationBucketId
}