package com.recon.cloud.service;

import com.recon.cloud.config.CloudConnectorProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StaticBearerTokenProvider implements BearerTokenProvider {

    private final CloudConnectorProperties properties;

    @Override
    public String getBearerToken() {
        return properties.getBearerToken();
    }
}
