package com.recon.xocs.domain;

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
public class XocsApiPage {
    @Builder.Default
    private List<XocsApiTransaction> records = new ArrayList<>();
    private boolean hasMore;
}
