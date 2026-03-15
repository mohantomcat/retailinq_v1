package com.recon.xocs.service;

import com.recon.xocs.config.XocsConnectorProperties;
import com.recon.xocs.domain.BearerTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StaticBearerTokenProvider implements BearerTokenProvider {

    private final XocsConnectorProperties properties;

    @Override
    public String getBearerToken() {
        return properties.getBearerToken();
    }
}
