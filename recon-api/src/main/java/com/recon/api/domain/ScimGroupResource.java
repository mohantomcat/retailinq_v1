package com.recon.api.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class ScimGroupResource {

    @Builder.Default
    private List<String> schemas = List.of("urn:ietf:params:scim:schemas:core:2.0:Group");

    private String id;
    private String displayName;
    private List<ScimGroupReference> members;
    private ScimMeta meta;
}
