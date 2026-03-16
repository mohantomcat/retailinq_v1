package com.recon.xocs.controller;

import com.recon.xocs.domain.XocsConnectorActionResponse;
import com.recon.xocs.domain.XocsConnectorStatusResponse;
import com.recon.xocs.domain.XocsReplayWindowRequest;
import com.recon.xocs.domain.XocsResetCheckpointRequest;
import com.recon.xocs.service.XocsConnectorAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/xocs-connector")
@RequiredArgsConstructor
public class XocsConnectorAdminController {

    private final XocsConnectorAdminService adminService;

    @GetMapping("/status")
    public XocsConnectorStatusResponse status() {
        return adminService.getStatus();
    }

    @PostMapping("/actions/download")
    public XocsConnectorActionResponse triggerDownload() {
        return adminService.triggerDownload();
    }

    @PostMapping("/actions/publish")
    public XocsConnectorActionResponse triggerPublish() {
        return adminService.triggerPublish();
    }

    @PostMapping("/actions/release-stale-claims")
    public XocsConnectorActionResponse releaseStaleClaims() {
        return adminService.releaseStaleClaims();
    }

    @PostMapping("/actions/requeue-failed")
    public XocsConnectorActionResponse requeueFailed() {
        return adminService.requeueFailed();
    }

    @PostMapping("/actions/reset-checkpoint")
    public XocsConnectorActionResponse resetCheckpoint(
            @RequestBody XocsResetCheckpointRequest request) {
        return adminService.resetCheckpoint(request);
    }

    @PostMapping("/actions/replay-window")
    public XocsConnectorActionResponse replayWindow(
            @RequestBody XocsReplayWindowRequest request) {
        return adminService.replayWindow(request);
    }
}
