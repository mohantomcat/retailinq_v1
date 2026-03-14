package com.recon.api.domain;

import lombok.Data;

import java.util.Set;

@Data
public class AssignStoresRequest {
    private Set<String> storeIds;
}