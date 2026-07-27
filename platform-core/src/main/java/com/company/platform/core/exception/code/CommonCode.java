package com.company.platform.core.exception.code;

import com.company.platform.core.i18n.I18nKey;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum CommonCode implements I18nKey {

    SUCCESS("success.common"),
    CREATED_SUCCESS("success.common.created"),
    UPDATED_SUCCESS("success.common.updated"),
    DELETED_SUCCESS("success.common.deleted"),
    SAVED_SUCCESS("success.common.saved"),
    PROCESSED_SUCCESS("success.common.processed"),
    ACCEPTED_SUCCESS("success.common.accepted"),

    SOMETHING_WENT_WRONG("error.common.something-went-wrong"),
    INTERNAL_SERVER_ERROR("error.common.internal-server-error"),
    SERVICE_UNAVAILABLE("error.common.service-unavailable"),
    REQUEST_FAILED("error.common.request-failed"),
    OPERATION_FAILED("error.common.operation-failed"),
    OPERATION_NOT_SUPPORTED("error.common.operation-not-supported"),
    METHOD_NOT_ALLOWED("error.common.method-not-allowed"),
    UNSUPPORTED_MEDIA_TYPE("error.common.unsupported-media-type"),
    NOT_ACCEPTABLE("error.common.not-acceptable"),
    TOO_MANY_REQUESTS("error.common.too-many-requests"),
    REQUEST_TIMEOUT("error.common.request-timeout"),
    GATEWAY_TIMEOUT("error.common.gateway-timeout"),
    BAD_GATEWAY("error.common.bad-gateway"),

    RESOURCE_NOT_FOUND("error.common.resource-not-found"),
    ENTITY_NOT_FOUND("error.common.entity-not-found"),
    URL_NOT_FOUND("error.common.url-not-found"),
    RESULT_NOT_FOUND("error.common.result-not-found"),
    DATA_NOT_FOUND("error.common.data-not-found"),

    RESOURCE_ALREADY_EXISTS("error.common.resource-already-exists"),
    ENTITY_ALREADY_EXISTS("error.common.entity-already-exists"),
    DUPLICATE_RESOURCE("error.common.duplicate-resource"),
    CONFLICT("error.common.conflict"),

    INVALID_REQUEST("error.common.invalid-request"),
    INVALID_INPUT("error.common.invalid-input"),
    INVALID_PARAMETER("error.common.invalid-parameter"),
    INVALID_PARAMETER_VALUE("error.common.invalid-parameter-value"),
    MISSING_PARAMETER("error.common.missing-parameter"),
    MISSING_HEADER("error.common.missing-header"),
    MISSING_REQUEST_BODY("error.common.missing-request-body"),
    INVALID_REQUEST_BODY("error.common.invalid-request-body"),
    INVALID_DATA_TYPE("error.common.invalid-data-type"),
    INVALID_FORMAT("error.common.invalid-format"),
    INVALID_FORMAT_OR_LENGTH("error.common.invalid-format-or-length"),
    OUT_OF_RANGE("error.common.out-of-range"),
    VALUE_TOO_SHORT("error.common.value-too-short"),
    VALUE_TOO_LONG("error.common.value-too-long"),
    REQUIRED_FIELD("error.common.required-field"),
    NULL_VALUE("error.common.null-value"),
    EMPTY_VALUE("error.common.empty-value"),
    BLANK_VALUE("error.common.blank-value");

    String key;
}
