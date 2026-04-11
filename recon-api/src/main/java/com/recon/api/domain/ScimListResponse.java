package com.recon.api.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScimListResponse<T> {

    @Builder.Default
    private List<String> schemas = List.of("urn:ietf:params:scim:api:messages:2.0:ListResponse");

    private int totalResults;
    private int startIndex;
    private int itemsPerPage;

    @JsonProperty("Resources")
    private List<T> resources;
}
