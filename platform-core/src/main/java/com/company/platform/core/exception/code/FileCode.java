package com.company.platform.core.exception.code;

import com.company.platform.core.i18n.I18nKey;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum FileCode implements I18nKey {

    UPLOADED_SUCCESS("success.file.uploaded"),
    DOWNLOADED_SUCCESS("success.file.downloaded"),
    DELETED_SUCCESS("success.file.deleted"),

    NOT_FOUND("error.file.not-found"),
    EMPTY("error.file.empty"),
    INVALID("error.file.invalid"),
    INVALID_NAME("error.file.invalid-name"),
    INVALID_FORMAT("error.file.invalid-format"),
    INVALID_EXTENSION("error.file.invalid-extension"),
    INVALID_CONTENT_TYPE("error.file.invalid-content-type"),
    SIZE_EXCEEDED("error.file.size-exceeded"),
    UPLOAD_FAILED("error.file.upload-failed"),
    DOWNLOAD_FAILED("error.file.download-failed"),
    DELETE_FAILED("error.file.delete-failed"),
    READ_FAILED("error.file.read-failed"),
    WRITE_FAILED("error.file.write-failed"),
    STORAGE_UNAVAILABLE("error.file.storage-unavailable"),
    DUPLICATE("error.file.duplicate"),
    CORRUPTED("error.file.corrupted");

    String key;


}
