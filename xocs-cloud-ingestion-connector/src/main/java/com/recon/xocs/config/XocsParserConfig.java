package com.recon.xocs.config;

import com.recon.publisher.config.PublisherConfig;
import com.recon.publisher.parser.PoslogStaxParser;
import com.recon.publisher.parser.PoslogStreamFactory;
import com.recon.publisher.parser.TimestampNormalizer;
import com.recon.publisher.util.ExternalIdBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class XocsParserConfig {

    @Bean
    public PublisherConfig publisherConfig(XocsConnectorProperties properties) {
        PublisherConfig config = new PublisherConfig();
        config.setOrgId(properties.getOrgId());
        config.setTenantId(properties.getTenantId());
        config.setTenantTimezone(properties.getTenantTimezone());
        return config;
    }

    @Bean
    public PoslogStreamFactory poslogStreamFactory() {
        return new PoslogStreamFactory();
    }

    @Bean
    public ExternalIdBuilder externalIdBuilder() {
        return new ExternalIdBuilder();
    }

    @Bean
    public TimestampNormalizer timestampNormalizer() {
        return new TimestampNormalizer();
    }

    @Bean
    public PoslogStaxParser poslogStaxParser(PublisherConfig publisherConfig,
                                             PoslogStreamFactory poslogStreamFactory,
                                             ExternalIdBuilder externalIdBuilder,
                                             TimestampNormalizer timestampNormalizer) {
        return new PoslogStaxParser(
                publisherConfig,
                poslogStreamFactory,
                externalIdBuilder,
                timestampNormalizer);
    }
}
