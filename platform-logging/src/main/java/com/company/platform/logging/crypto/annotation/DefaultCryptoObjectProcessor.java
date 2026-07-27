package com.company.platform.logging.crypto.annotation;

import com.company.platform.logging.annotation.crypto.DecryptValue;
import com.company.platform.logging.annotation.crypto.EncryptValue;
import com.company.platform.logging.api.crypto.CryptoObjectProcessor;
import com.company.platform.logging.api.crypto.CryptoService;
import com.company.platform.logging.domain.exception.PlatformCryptoException;
import com.company.platform.logging.domain.model.CryptoRequest;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public final class DefaultCryptoObjectProcessor implements CryptoObjectProcessor {
    private final CryptoService crypto;

    public DefaultCryptoObjectProcessor(CryptoService crypto) {
        this.crypto = crypto;
    }

    @Override
    public <T> T encryptAnnotatedFields(T source) {
        return process(source, true);
    }

    @Override
    public <T> T decryptAnnotatedFields(T source) {
        return process(source, false);
    }

    private <T> T process(T source, boolean encrypt) {
        if (source == null) {
            return null;
        }
        for (Field field : fields(source.getClass())) {
            EncryptValue encryptValue = field.getAnnotation(EncryptValue.class);
            DecryptValue decryptValue = field.getAnnotation(DecryptValue.class);
            if (encrypt && encryptValue == null || !encrypt && decryptValue == null) {
                continue;
            }
            if (Modifier.isFinal(field.getModifiers()) || !field.trySetAccessible()) {
                throw failure("Annotated crypto field must be mutable and accessible");
            }
            try {
                Object value = field.get(source);
                if (value == null) {
                    continue;
                }
                field.set(source, encrypt
                    ? transformEncrypt(value, encryptValue)
                    : transformDecrypt(value, decryptValue));
            } catch (IllegalAccessException exception) {
                throw failure("Annotated crypto field cannot be updated");
            }
        }
        return source;
    }

    private Object transformEncrypt(Object value, EncryptValue annotation) {
        CryptoRequest request = CryptoRequest.builder()
            .provider(annotation.provider()).algorithm(annotation.algorithm())
            .keyAlias(annotation.keyAlias()).strategyBean(annotation.strategyBean()).build();
        if (value instanceof String text) {
            return crypto.encrypt(text, request);
        }
        if (value instanceof byte[] bytes) {
            return crypto.encrypt(bytes, request);
        }
        throw failure("Annotated crypto field supports only String and byte[]");
    }

    private Object transformDecrypt(Object value, DecryptValue annotation) {
        CryptoRequest request = CryptoRequest.builder()
            .provider(annotation.provider()).algorithm(annotation.algorithm())
            .keyAlias(annotation.keyAlias()).strategyBean(annotation.strategyBean()).build();
        if (value instanceof String text) {
            return crypto.decrypt(text, request);
        }
        if (value instanceof byte[] bytes) {
            return crypto.decrypt(bytes, request);
        }
        throw failure("Annotated crypto field supports only String and byte[]");
    }

    private static List<Field> fields(Class<?> type) {
        ArrayList<Field> fields = new ArrayList<>();
        for (Class<?> current = type;
             current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    private static PlatformCryptoException failure(String detail) {
        return new PlatformCryptoException("PLATFORM.CRYPTO.FIELD", detail);
    }
}
