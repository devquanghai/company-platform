package com.company.platform.core.exception.internal.adapter.helper;

import com.company.platform.core.configuration.properties.PlatformCoreExceptionProperties;
import com.company.platform.core.i18n.I18nService;
import com.company.platform.core.rest.factory.ResponseMetadataFactory;
import lombok.Getter;

@Getter
public final class HandlerExceptionHelper {

    private final JsonExceptionHelper json;

    private final ValidationExceptionHelper validation;

    private final ResponseExceptionHelper response;

    public HandlerExceptionHelper(
        PlatformCoreExceptionProperties properties,
        I18nService i18n,
        ResponseMetadataFactory metadataFactory
    ) {

        this.json =
            new JsonExceptionHelper(i18n);

        this.validation =
            new ValidationExceptionHelper(
                properties,
                i18n
            );

        this.response =
            new ResponseExceptionHelper(
                metadataFactory,
                i18n
            );
    }
}
