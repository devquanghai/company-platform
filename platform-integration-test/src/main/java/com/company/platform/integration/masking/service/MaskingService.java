package com.company.platform.integration.masking.service;

import com.company.platform.core.json.JsonMapperHelper;
import com.company.platform.integration.masking.dto.request.MaskingRequest;
import com.company.platform.integration.masking.dto.response.MaskingResponse;
import com.company.platform.integration.masking.mapper.MaskingMapper;
import com.company.platform.logging.api.masking.DataMaskingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaskingService {
    private final JsonMapperHelper jsonMapperHelper;
    private final MaskingMapper maskingMapper;
    private final DataMaskingService masking;

    public MaskingRequest maskRawRequest(MaskingRequest request) {
        log.info("Received request: {}", jsonMapperHelper.toJson(request));
        return request;
    }

    public MaskingResponse maskingAnotation(MaskingRequest request) {
        log.info("Received request: {}", jsonMapperHelper.toJson(request));
        return maskingMapper.toTarget(request);
    }

    public MaskingResponse maskingUseBean(MaskingRequest request) {
        log.info("Received request: {}", jsonMapperHelper.toJson(request));
        var resp = maskingMapper.toTarget(request);
        return jsonMapperHelper.convert(masking.sanitize(resp), MaskingResponse.class);
    }
}
