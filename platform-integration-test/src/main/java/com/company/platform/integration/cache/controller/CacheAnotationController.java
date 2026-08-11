package com.company.platform.integration.cache.controller;

import com.company.platform.core.rest.response.ApiResponse;
import com.company.platform.core.rest.response.ResponseMetadata;
import com.company.platform.integration.cache.dto.request.CacheRequest;
import com.company.platform.integration.cache.service.CacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cache")
@RequiredArgsConstructor
public class CacheAnotationController {
    private final CacheService cacheService;

    @PostMapping("/get")
    public ApiResponse<List<CacheRequest>> get(@RequestParam String key) throws InterruptedException {
        return ApiResponse.success(cacheService.get(key));
    }

    @DeleteMapping("/evict")
    public ApiResponse<Void> evict(@RequestParam String key) {
        cacheService.evict(key);
        return ApiResponse.success(null, ResponseMetadata.empty());
    }
}
