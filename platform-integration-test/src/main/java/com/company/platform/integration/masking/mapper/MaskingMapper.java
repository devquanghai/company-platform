package com.company.platform.integration.masking.mapper;

import com.company.platform.core.mapper.PlatformMapper;
import com.company.platform.integration.masking.dto.request.MaskingRequest;
import com.company.platform.integration.masking.dto.response.MaskingResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MaskingMapper extends PlatformMapper<MaskingRequest, MaskingResponse> {

    @Override
    @Mapping(
        target = "dateOfBirth",
        expression = "java(source.getDateOfBirth() != null ? source.getDateOfBirth().toString() : null)"
    )
    MaskingResponse toTarget(MaskingRequest source);
}
