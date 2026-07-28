package com.company.platform.logging.crypto.annotation;

import com.company.platform.logging.annotation.crypto.DecryptResult;
import com.company.platform.logging.annotation.crypto.DecryptValue;
import com.company.platform.logging.annotation.crypto.EncryptResult;
import com.company.platform.logging.annotation.crypto.EncryptValue;
import com.company.platform.logging.api.crypto.CryptoService;
import com.company.platform.logging.domain.exception.PlatformCryptoException;
import com.company.platform.logging.domain.model.CryptoRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public final class CryptoAnnotationAspect {
    private final CryptoService crypto;

    public CryptoAnnotationAspect(CryptoService crypto) {
        this.crypto = crypto;
    }

    @Around("""
        !within(com.company.platform.logging..*) && (
            @annotation(com.company.platform.logging.annotation.crypto.EncryptResult)
            || @annotation(com.company.platform.logging.annotation.crypto.DecryptResult)
            || execution(public * *(..,
                @com.company.platform.logging.annotation.crypto.EncryptValue (*), ..))
            || execution(public * *(..,
                @com.company.platform.logging.annotation.crypto.DecryptValue (*), ..))
        )
        """)
    public Object process(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = method(joinPoint);
        Annotation[][] annotations = method.getParameterAnnotations();
        EncryptResult encryptResult = method.getAnnotation(EncryptResult.class);
        DecryptResult decryptResult = method.getAnnotation(DecryptResult.class);
        boolean annotated = encryptResult != null || decryptResult != null
            || hasArgumentAnnotation(annotations);
        if (!annotated) {
            return joinPoint.proceed();
        }
        Object[] arguments = joinPoint.getArgs().clone();
        for (int index = 0; index < annotations.length; index++) {
            for (Annotation annotation : annotations[index]) {
                if (annotation instanceof EncryptValue encrypt) {
                    arguments[index] = encrypt(arguments[index], request(encrypt));
                } else if (annotation instanceof DecryptValue decrypt) {
                    arguments[index] = decrypt(arguments[index], request(decrypt));
                }
            }
        }
        Object result = joinPoint.proceed(arguments);
        if (encryptResult != null) {
            return encrypt(result, request(encryptResult));
        }
        if (decryptResult != null) {
            return decrypt(result, request(decryptResult));
        }
        return result;
    }

    private Object encrypt(Object value, CryptoRequest request) {
        if (value instanceof String text) {
            return crypto.encrypt(text, request);
        }
        if (value instanceof byte[] bytes) {
            return crypto.encrypt(bytes, request);
        }
        throw unsupported();
    }

    private Object decrypt(Object value, CryptoRequest request) {
        if (value instanceof String text) {
            return crypto.decrypt(text, request);
        }
        if (value instanceof byte[] bytes) {
            return crypto.decrypt(bytes, request);
        }
        throw unsupported();
    }

    private static boolean hasArgumentAnnotation(Annotation[][] annotations) {
        for (Annotation[] values : annotations) {
            for (Annotation value : values) {
                if (value instanceof EncryptValue || value instanceof DecryptValue) {
                    return true;
                }
            }
        }
        return false;
    }

    private static CryptoRequest request(EncryptValue value) {
        return CryptoRequest.builder().provider(value.provider()).algorithm(value.algorithm())
            .keyAlias(value.keyAlias()).strategyBean(value.strategyBean()).build();
    }
    private static CryptoRequest request(DecryptValue value) {
        return CryptoRequest.builder().provider(value.provider()).algorithm(value.algorithm())
            .keyAlias(value.keyAlias()).strategyBean(value.strategyBean()).build();
    }
    private static CryptoRequest request(EncryptResult value) {
        return CryptoRequest.builder().provider(value.provider()).algorithm(value.algorithm())
            .keyAlias(value.keyAlias()).strategyBean(value.strategyBean()).build();
    }
    private static CryptoRequest request(DecryptResult value) {
        return CryptoRequest.builder().provider(value.provider()).algorithm(value.algorithm())
            .keyAlias(value.keyAlias()).strategyBean(value.strategyBean()).build();
    }

    private static Method method(ProceedingJoinPoint joinPoint) {
        Method signature = ((MethodSignature) joinPoint.getSignature()).getMethod();
        return BridgeMethodResolver.findBridgedMethod(
            AopUtils.getMostSpecificMethod(signature, joinPoint.getTarget().getClass()));
    }

    private static PlatformCryptoException unsupported() {
        return new PlatformCryptoException(
            "PLATFORM.CRYPTO.ANNOTATION_TYPE",
            "Crypto annotations support only String and byte[]");
    }
}
