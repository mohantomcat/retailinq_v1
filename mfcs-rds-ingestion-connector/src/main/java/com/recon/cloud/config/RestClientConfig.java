package com.recon.cloud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient(CloudConnectorProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(
                Duration.ofMillis(properties.getReadTimeoutMs()));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public RetryTemplate cloudRestRetryTemplate(CloudConnectorProperties properties) {
        RetryTemplate retryTemplate = new RetryTemplate();

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(properties.getRetryMaxAttempts());
        retryTemplate.setRetryPolicy(retryPolicy);

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(properties.getRetryInitialIntervalMs());
        backOffPolicy.setMultiplier(properties.getRetryMultiplier());
        backOffPolicy.setMaxInterval(properties.getRetryMaxIntervalMs());
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }
}
