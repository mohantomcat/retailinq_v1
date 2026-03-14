package com.recon.publisher.controller;

import com.recon.publisher.domain.XstorePublisherActionResponse;
import com.recon.publisher.domain.XstorePublisherStatusResponse;
import com.recon.publisher.service.XstorePublisherAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/xstore-publisher")
@RequiredArgsConstructor
public class XstorePublisherAdminController {

    private final XstorePublisherAdminService adminService;

    @GetMapping("/status")
    public XstorePublisherStatusResponse status() {
        return adminService.getStatus();
    }

    @PostMapping("/actions/publish")
    public XstorePublisherActionResponse publish() {
        return adminService.triggerPublish();
    }

    @PostMapping("/actions/release-stale-claims")
    public XstorePublisherActionResponse releaseStaleClaims() {
        return adminService.releaseStaleClaims();
    }
}
