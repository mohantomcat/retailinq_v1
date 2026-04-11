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
public class ScimUserRequest {
    private List<String> schemas;
    private String externalId;

    @JsonProperty("userName")
    private String userName;

    private String displayName;
    private Boolean active;
    private ScimName name;
    private List<ScimEmail> emails;
    private List<ScimGroupReference> groups;
}
