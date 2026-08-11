package com.company.platform.integration.cache.controller;

import com.company.platform.cache.api.lock.DistributedLockOperations;
import com.company.platform.cache.api.lock.LockOptions;
import com.company.platform.core.rest.response.ApiResponse;
import com.company.platform.core.rest.response.ResponseMetadata;
import com.company.platform.integration.cache.dto.request.CacheRequest;
import com.company.platform.integration.cache.dto.response.OptimisticLockResponse;
import com.company.platform.integration.cache.dto.response.VersionedCacheResponse;
import com.company.platform.integration.cache.service.CacheBeanService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

@RestController
@RequestMapping("/cache-bean")
@RequiredArgsConstructor
public class CacheBeanController {
    private final CacheBeanService cacheService;
    private final ObjectProvider<DistributedLockOperations> distributedLocks;

    @PostMapping("/get")
    public ApiResponse<List<CacheRequest>> get(@RequestParam String key) throws InterruptedException {
        return ApiResponse.success(cacheService.get(key));
    }

    @DeleteMapping("/evict")
    public ApiResponse<Void> evict(@RequestParam String key) {
        cacheService.evict(key);
        return ApiResponse.success(null, ResponseMetadata.empty());
    }

    @PostMapping("/distributed-lock")
    public ApiResponse<VersionedCacheResponse> executeWithDistributedLock(
        @RequestParam String key,
        @RequestParam String value
    ) {
        requireKey(key);
        DistributedLockOperations locks = distributedLocks.getIfAvailable();
        if (locks == null) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "DistributedLockOperations adapter is not configured");
        }
        LockOptions options = LockOptions.builder()
            .waitTime(Duration.ofSeconds(2))
            .leaseTime(Duration.ofSeconds(30))
            .build();
        VersionedCacheResponse created = cacheCall(() -> locks.executeWithLock(
            cacheService.distributedLockName(key), options,
            () -> cacheService.initializeVersioned(key, value)));
        return ApiResponse.success(requireCreated(created));
    }

    @PostMapping("/optimistic-lock")
    public ApiResponse<VersionedCacheResponse> initializeOptimisticLock(
        @RequestParam String key,
        @RequestParam String value
    ) {
        requireKey(key);
        return ApiResponse.success(requireCreated(cacheCall(
            () -> cacheService.initializeVersioned(key, value))));
    }

    @GetMapping("/optimistic-lock")
    public ApiResponse<VersionedCacheResponse> getOptimisticLock(
        @RequestParam String key
    ) {
        requireKey(key);
        VersionedCacheResponse value = cacheCall(() -> cacheService.getVersioned(key));
        if (value == null) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Versioned cache entry was not found");
        }
        return ApiResponse.success(value);
    }

    @PutMapping("/optimistic-lock")
    public ApiResponse<OptimisticLockResponse> updateOptimisticLock(
        @RequestParam String key,
        @RequestParam long expectedVersion,
        @RequestParam String value
    ) {
        requireKey(key);
        if (expectedVersion < 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "expectedVersion must be non-negative");
        }
        OptimisticLockResponse result = cacheCall(
            () -> cacheService.updateIfVersion(key, expectedVersion, value));
        if ("FAILED".equals(result.status())) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Optimistic cache operation is unavailable");
        }
        return ApiResponse.success(result);
    }

    private VersionedCacheResponse requireCreated(VersionedCacheResponse value) {
        if (value == null) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Versioned cache entry already exists");
        }
        return value;
    }

    private void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "key must not be blank");
        }
    }

    private <T> T cacheCall(Supplier<T> action) {
        try {
            return action.get();
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Cache coordination operation is unavailable", exception);
        }
    }
}
