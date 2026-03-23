package com.recon.cloud.actuator;

import com.recon.cloud.domain.CloudConnectorStatusResponse;
import com.recon.cloud.service.CloudConnectorAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "cloudConnector")
@RequiredArgsConstructor
public class CloudConnectorEndpoint {

    private final CloudConnectorAdminService adminService;

    @ReadOperation
    public CloudConnectorStatusResponse status() {
        return adminService.getStatus();
    }
}
