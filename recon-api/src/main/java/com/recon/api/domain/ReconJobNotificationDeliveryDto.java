package com.recon.api.domain;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class ReconJobNotificationDeliveryDto {
    UUID id;
    String channelType;
    String destination;
    String deliveryStatus;
    Integer responseCode;
    String errorMessage;
    Object payload;
    String createdAt;
}
