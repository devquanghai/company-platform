package com.company.platform.logging.crypto.annotation;

import com.company.platform.logging.annotation.crypto.DecryptResult;
import com.company.platform.logging.annotation.crypto.DecryptValue;
import com.company.platform.logging.annotation.crypto.EncryptResult;
import com.company.platform.logging.annotation.crypto.EncryptValue;
import com.company.platform.logging.api.crypto.CryptoService;
import com.company.platform.logging.autoconfigure.properties.PlatformLoggingProperties;
import com.company.platform.logging.domain.exception.PlatformCryptoException;
import com.company.platform.logging.domain.model.CryptoAlgorithm;
import com.company.platform.logging.domain.model.CryptoProviderType;
import com.company.platform.logging.domain.model.CryptoRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoAnnotationAspectTest {

    private TrackingCryptoService crypto;
    private PlatformLoggingProperties.CryptoProperties properties;
    private CryptoAnnotationAspect aspect;

    @BeforeEach
    void setUp() {
        crypto = new TrackingCryptoService();
        properties = new PlatformLoggingProperties.CryptoProperties();
        aspect = new CryptoAnnotationAspect(crypto, properties);
    }

    @Test
    void encryptsAndDecryptsAnnotatedStringArgumentsWithFullRequestMetadata() throws Throwable {
        JoinPointStub stub = stub("transform", "done", "plain", "cipher");

        assertThat(aspect.process(stub.proxy)).isEqualTo("done");
        assertThat(stub.proceededArguments).containsExactly("enc:plain", "dec:cipher");
        assertThat(crypto.requests).hasSize(2);
        CryptoRequest encryptRequest = crypto.requests.get(0);
        CryptoRequest decryptRequest = crypto.requests.get(1);
        assertThat(encryptRequest.getProvider()).isEqualTo(CryptoProviderType.JCA);
        assertThat(encryptRequest.getAlgorithm()).isEqualTo(CryptoAlgorithm.AES_GCM_256);
        assertThat(encryptRequest.getKeyAlias()).isEqualTo("encrypt-key");
        assertThat(encryptRequest.getStrategyBean()).isEqualTo("primary");
        assertThat(decryptRequest.getProvider()).isEqualTo(CryptoProviderType.JASYPT);
        assertThat(decryptRequest.getAlgorithm()).isEqualTo(CryptoAlgorithm.PBE);
        assertThat(decryptRequest.getKeyAlias()).isEqualTo("decrypt-key");
        assertThat(decryptRequest.getStrategyBean()).isEqualTo("legacy");
    }

    @Test
    void supportsByteArrayArgumentsAndEncryptedOrDecryptedResults() throws Throwable {
        byte[] plain = "plain".getBytes(StandardCharsets.UTF_8);
        byte[] cipher = "cipher".getBytes(StandardCharsets.UTF_8);
        JoinPointStub bytes = stub("bytes", "bytes", plain, cipher);
        assertThat(aspect.process(bytes.proxy)).isEqualTo("bytes");
        assertThat(bytes.proceededArguments[0]).isEqualTo(TrackingCryptoService.ENCRYPTED_BYTES);
        assertThat(bytes.proceededArguments[1]).isEqualTo(TrackingCryptoService.DECRYPTED_BYTES);

        JoinPointStub encryptResult = stub("encryptResult", "result");
        assertThat(aspect.process(encryptResult.proxy)).isEqualTo("enc:result");
        assertThat(crypto.requests.get(2).getKeyAlias()).isEqualTo("result-key");
        assertThat(crypto.requests.get(2).getStrategyBean()).isEqualTo("resultStrategy");

        JoinPointStub decryptResult = stub("decryptResult", cipher);
        assertThat(aspect.process(decryptResult.proxy))
            .isSameAs(TrackingCryptoService.DECRYPTED_BYTES);
        assertThat(crypto.requests.get(3).getKeyAlias()).isEqualTo("result-key");
    }

    @Test
    void proceedsNormallyForUnannotatedMethodsOrAllowedDisabledAnnotations() throws Throwable {
        JoinPointStub plain = stub("plain", "plain-result", "value");
        assertThat(aspect.process(plain.proxy)).isEqualTo("plain-result");
        assertThat(plain.proceedWithoutArguments).isTrue();
        assertThat(crypto.requests).isEmpty();

        properties.setEnabled(false);
        properties.setFailIfDisabledAnnotationUsed(false);
        JoinPointStub annotated = stub("encryptResult", "raw");
        assertThat(aspect.process(annotated.proxy)).isEqualTo("raw");
        assertThat(annotated.proceedWithoutArguments).isTrue();
    }

    @Test
    void failsClosedWhenCryptoOrAnnotationsAreDisabledOrServiceIsMissing() throws Exception {
        JoinPointStub joinPoint = stub("encryptResult", "value");
        properties.setEnabled(false);
        assertThatThrownBy(() -> aspect.process(joinPoint.proxy))
            .isInstanceOf(PlatformCryptoException.class)
            .hasMessageContaining("Crypto annotation");

        properties.setEnabled(true);
        properties.setAnnotationEnabled(false);
        assertThatThrownBy(() -> aspect.process(joinPoint.proxy))
            .isInstanceOf(PlatformCryptoException.class);

        properties.setAnnotationEnabled(true);
        CryptoAnnotationAspect missingService = new CryptoAnnotationAspect(null, properties);
        assertThatThrownBy(() -> missingService.process(joinPoint.proxy))
            .isInstanceOf(PlatformCryptoException.class);
        assertThat(joinPoint.proceedCount).isZero();
    }

    @Test
    void rejectsUnsupportedAnnotatedArgumentAndResultTypes() throws Exception {
        JoinPointStub argument = stub("unsupported", "unused", 42);
        assertThatThrownBy(() -> aspect.process(argument.proxy))
            .isInstanceOf(PlatformCryptoException.class)
            .hasMessageContaining("String and byte[]");
        assertThat(argument.proceedCount).isZero();

        JoinPointStub result = stub("unsupportedResult", 42);
        assertThatThrownBy(() -> aspect.process(result.proxy))
            .isInstanceOf(PlatformCryptoException.class)
            .hasMessageContaining("String and byte[]");
        assertThat(result.proceedCount).isOne();
    }

    private static JoinPointStub stub(
        String methodName, Object result, Object... arguments
    ) throws NoSuchMethodException {
        Class<?>[] types = new Class<?>[arguments.length];
        for (int index = 0; index < arguments.length; index++) {
            types[index] = switch (methodName) {
                case "unsupported" -> Integer.class;
                case "bytes" -> byte[].class;
                default -> String.class;
            };
        }
        return new JoinPointStub(new Service(), Service.class.getMethod(methodName, types),
            arguments, result);
    }

    private static final class JoinPointStub {
        private final ProceedingJoinPoint proxy;
        private Object[] proceededArguments;
        private boolean proceedWithoutArguments;
        private int proceedCount;

        private JoinPointStub(Object target, Method targetMethod, Object[] arguments, Object result) {
            MethodSignature signature = (MethodSignature) Proxy.newProxyInstance(
                MethodSignature.class.getClassLoader(), new Class<?>[] {MethodSignature.class},
                (ignored, invoked, values) -> invoked.getName().equals("getMethod")
                    ? targetMethod : defaultValue(invoked.getReturnType()));
            proxy = (ProceedingJoinPoint) Proxy.newProxyInstance(
                ProceedingJoinPoint.class.getClassLoader(),
                new Class<?>[] {ProceedingJoinPoint.class},
                (ignored, invoked, values) -> {
                    return switch (invoked.getName()) {
                        case "getSignature" -> signature;
                        case "getTarget", "getThis" -> target;
                        case "getArgs" -> arguments;
                        case "proceed" -> {
                            proceedCount++;
                            if (values == null) {
                                proceedWithoutArguments = true;
                            } else {
                                proceededArguments = ((Object[]) values[0]).clone();
                            }
                            yield result;
                        }
                        default -> defaultValue(invoked.getReturnType());
                    };
                });
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        return null;
    }

    private static final class TrackingCryptoService implements CryptoService {
        private static final byte[] ENCRYPTED_BYTES = new byte[] {1, 2};
        private static final byte[] DECRYPTED_BYTES = new byte[] {3, 4};
        private final List<CryptoRequest> requests = new ArrayList<>();

        @Override public String encrypt(String plaintext, CryptoRequest request) {
            requests.add(request);
            return "enc:" + plaintext;
        }

        @Override public String decrypt(String ciphertext, CryptoRequest request) {
            requests.add(request);
            return "dec:" + ciphertext;
        }

        @Override public byte[] encrypt(byte[] plaintext, CryptoRequest request) {
            requests.add(request);
            return ENCRYPTED_BYTES;
        }

        @Override public byte[] decrypt(byte[] ciphertext, CryptoRequest request) {
            requests.add(request);
            return DECRYPTED_BYTES;
        }
    }

    static class Service {
        public String transform(
            @EncryptValue(keyAlias = "encrypt-key", strategyBean = "primary") String plain,
            @DecryptValue(
                provider = CryptoProviderType.JASYPT, algorithm = CryptoAlgorithm.PBE,
                keyAlias = "decrypt-key", strategyBean = "legacy"
            ) String encrypted
        ) {
            return plain + encrypted;
        }

        public String bytes(
            @EncryptValue(keyAlias = "bytes-key") byte[] plain,
            @DecryptValue(keyAlias = "bytes-key") byte[] encrypted
        ) {
            return plain.length + ":" + encrypted.length;
        }

        @EncryptResult(keyAlias = "result-key", strategyBean = "resultStrategy")
        public String encryptResult() { return "result"; }

        @DecryptResult(keyAlias = "result-key", strategyBean = "resultStrategy")
        public byte[] decryptResult() { return new byte[0]; }

        public String plain(String value) { return value; }

        public String unsupported(@EncryptValue(keyAlias = "key") Integer value) {
            return value.toString();
        }

        @EncryptResult(keyAlias = "key")
        public Integer unsupportedResult() { return 42; }
    }
}
