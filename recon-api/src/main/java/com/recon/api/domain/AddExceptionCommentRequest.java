package com.recon.api.domain;

import lombok.Data;

@Data
public class AddExceptionCommentRequest {
    private String reconView;
    private String commentText;
}
