package com.recon.cloud.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloudApiPage {
    @Builder.Default
    private List<CloudApiTransaction> records = new ArrayList<>();
    private String nextCursor;
    private boolean hasMore;
}
