package com.recon.cloud.controller;

import com.recon.cloud.domain.CloudConnectorActionResponse;
import com.recon.cloud.domain.CloudConnectorStatusResponse;
import com.recon.cloud.domain.ResetCheckpointRequest;
import com.recon.cloud.service.CloudConnectorAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/cloud-connector")
@RequiredArgsConstructor
public class CloudConnectorAdminController {

    private final CloudConnectorAdminService adminService;

    @GetMapping("/status")
    public CloudConnectorStatusResponse status() {
        return adminService.getStatus();
    }

    @PostMapping("/actions/download")
    public CloudConnectorActionResponse triggerDownload() {
        return adminService.triggerDownload();
    }

    @PostMapping("/actions/publish")
    public CloudConnectorActionResponse triggerPublish() {
        return adminService.triggerPublish();
    }

    @PostMapping("/actions/reset-checkpoint")
    public CloudConnectorActionResponse resetCheckpoint(
            @Valid @RequestBody ResetCheckpointRequest request) {
        return adminService.resetCheckpoint(
                Instant.parse(request.getLastSuccessTimestamp()),
                request.getLastCursorId());
    }

    @PostMapping("/actions/release-stale-claims")
    public CloudConnectorActionResponse releaseStaleClaims() {
        return adminService.releaseStaleClaims();
    }

    @PostMapping("/actions/requeue-failed")
    public CloudConnectorActionResponse requeueFailed() {
        return adminService.requeueFailed();
    }

    @PostMapping("/actions/requeue-dlq")
    public CloudConnectorActionResponse requeueDlq() {
        return adminService.requeueDlq();
    }
}
