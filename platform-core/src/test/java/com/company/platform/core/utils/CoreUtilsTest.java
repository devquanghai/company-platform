package com.company.platform.core.utils;

import com.company.platform.core.exception.PlatformInfrastructureException;
import com.company.platform.core.web.wrapper.CachedBodyHttpServletRequestWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreUtilsTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void aesUsesAuthenticatedEncryptionKeyDerivationAndOaepWrapping() throws Exception {
        byte[] rawKey = AesUtils.generateAesKey();
        String key = Base64Utils.encode(rawKey);
        assertThat(rawKey).hasSize(32);
        assertThat(AesUtils.generateIv()).hasSize(12);
        assertThat(AesUtils.buildGcmSpec(new byte[12]).getTLen()).isEqualTo(128);
        assertThat(AesUtils.generateKey(key).getEncoded()).isEqualTo(rawKey);
        assertThat(AesUtils.generateKey(Base64Utils.encode(new byte[16])).getEncoded()).hasSize(16);
        assertThat(AesUtils.generateKey(Base64Utils.encode(new byte[24])).getEncoded()).hasSize(24);
        assertThat(AesUtils.getKeyFromPassword("password", "salt").getEncoded()).hasSize(32);

        String encrypted = AesUtils.encrypt("Tiếng Việt", key);
        assertThat(AesUtils.decrypt(encrypted, key)).isEqualTo("Tiếng Việt");
        byte[] hybrid = AesUtils.hybridEncrypt("payload".getBytes(StandardCharsets.UTF_8), rawKey);
        assertThat(AesUtils.hybridDecrypt(hybrid, rawKey))
            .isEqualTo("payload".getBytes(StandardCharsets.UTF_8));

        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        byte[] wrapped = AesUtils.encryptAesKey(rawKey, pair.getPublic());
        assertThat(AesUtils.decryptAesKey(wrapped, pair.getPrivate())).isEqualTo(rawKey);

        byte[] legacyPayload = legacyGcm("legacy".getBytes(StandardCharsets.UTF_8), rawKey);
        assertThat(AesUtils.decrypt(Base64.getEncoder().encodeToString(legacyPayload), key))
            .isEqualTo("legacy");
        assertThat(AesUtils.hybridDecrypt(legacyPayload, rawKey))
            .isEqualTo("legacy".getBytes(StandardCharsets.UTF_8));
        Cipher legacyRsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        legacyRsa.init(Cipher.ENCRYPT_MODE, pair.getPublic());
        assertThat(AesUtils.decryptAesKey(legacyRsa.doFinal(rawKey), pair.getPrivate()))
            .isEqualTo(rawKey);
    }

    @Test
    void aesNormalizesCryptographicFailuresWithoutLeakingInput() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            AesUtils.generateKey(Base64Utils.encode(new byte[10])));
        assertCryptoFailure(() -> AesUtils.encrypt("secret", Base64Utils.encode(new byte[10])));
        assertCryptoFailure(() -> AesUtils.decrypt("invalid", Base64Utils.encode(new byte[32])));
        assertCryptoFailure(() -> AesUtils.decrypt(
            Base64Utils.encode(new byte[13]), Base64Utils.encode(new byte[32])));
        assertCryptoFailure(() -> AesUtils.hybridDecrypt(new byte[12], new byte[32]));
        assertCryptoFailure(() -> AesUtils.hybridDecrypt(new byte[13], new byte[32]));
        assertCryptoFailure(() -> AesUtils.hybridEncrypt(new byte[]{1}, new byte[10]));
        assertCryptoFailure(() -> AesUtils.encryptAesKey(new byte[32], null));
        assertCryptoFailure(() -> AesUtils.decryptAesKey(new byte[]{1}, null));
    }

    @Test
    void base64SupportsStandardAndUrlSafeUtf8AndNullContracts() {
        assertThat(Base64Utils.decodeToString(Base64Utils.encode("Xin chào")))
            .isEqualTo("Xin chào");
        assertThat(Base64Utils.decode(Base64Utils.encode(new byte[]{1, 2})))
            .containsExactly(1, 2);
        assertThat(Base64Utils.decodeUrlSafeToString(Base64Utils.encodeUrlSafe("a?b")))
            .isEqualTo("a?b");
        assertThat(Base64Utils.decodeUrlSafe(Base64Utils.encodeUrlSafe(new byte[]{3, 4})))
            .containsExactly(3, 4);
        assertThatNullPointerException().isThrownBy(() -> Base64Utils.encode((String) null));
        assertThatNullPointerException().isThrownBy(() -> Base64Utils.encode((byte[]) null));
        assertThatNullPointerException().isThrownBy(() -> Base64Utils.encodeUrlSafe((String) null));
        assertThatNullPointerException().isThrownBy(() -> Base64Utils.encodeUrlSafe((byte[]) null));
        assertThatNullPointerException().isThrownBy(() -> Base64Utils.decode(null));
        assertThatNullPointerException().isThrownBy(() -> Base64Utils.decodeUrlSafe(null));
    }

    @Test
    void collectionUtilitiesCoverNullDuplicatesDifferencesAndLookups() {
        assertThat(CollectionUtils.findDuplicateElements((String[]) null)).isEmpty();
        assertThat(CollectionUtils.findDuplicateElements((List<String>) null)).isEmpty();
        assertThat(CollectionUtils.findDuplicateElements(
            new String[]{" A ", "a", null, "B"})).containsExactly("a");
        assertThat(CollectionUtils.findDuplicateElements(List.of(1, 1, 2))).containsExactly(1);
        assertThat(CollectionUtils.isEmpty((Object[]) null)).isTrue();
        assertThat(CollectionUtils.isEmpty(new Object[0])).isTrue();
        assertThat(CollectionUtils.isEmpty(new Object[]{null})).isTrue();
        assertThat(CollectionUtils.isEmpty(new Object[]{1})).isFalse();
        assertThat(CollectionUtils.isEmpty((List<?>) null)).isTrue();
        assertThat(CollectionUtils.isEmpty(List.of())).isTrue();
        assertThat(CollectionUtils.isNotEmpty(List.of(1))).isTrue();
        assertThat(CollectionUtils.isNotEmpty(List.of())).isFalse();
        assertThat(CollectionUtils.findDifferentElements(null, List.of(1))).isEmpty();
        assertThat(CollectionUtils.findDifferentElements(List.of(1, 2), null)).containsExactlyInAnyOrder(1, 2);
        assertThat(CollectionUtils.findDifferentElements(List.of(1, 2), List.of(2))).containsExactly(1);
        assertThat(CollectionUtils.toString(null)).isEqualTo("[]");
        assertThat(CollectionUtils.toString(List.of(1, 2))).isEqualTo("[1,2]");
        assertThat(CollectionUtils.getValuesByKeys(null, Map.of())).isEmpty();
        assertThat(CollectionUtils.getValuesByKeys(
            List.of("one", "missing"), Map.of("one", 1))).containsExactly(1);
        assertThatNullPointerException().isThrownBy(() ->
            CollectionUtils.getValuesByKeys(List.of("one"), null));
    }

    @Test
    void cookieUtilitiesEmitOneSecureSameSiteHeaderAndValidateArguments() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie("token", "value"));
        assertThat(CookieUtils.getCookieValue(request, "token")).isEqualTo("value");
        assertThat(CookieUtils.getCookieValue(request, "missing")).isNull();
        assertThat(CookieUtils.getCookieValue(null, "token")).isNull();
        assertThat(CookieUtils.getCookieValue(new MockHttpServletRequest(), "token")).isNull();
        assertThat(CookieUtils.getCookieValue(request, null)).isNull();

        MockHttpServletResponse response = new MockHttpServletResponse();
        CookieUtils.addHttpOnlyCookie(response, "token", null, 60);
        assertThat(response.getHeaders("Set-Cookie")).singleElement()
            .asString().contains("HttpOnly", "Secure", "SameSite=Strict", "Max-Age=60");
        CookieUtils.deleteCookie(response, "token");
        assertThat(response.getHeaders("Set-Cookie")).hasSize(2);
        assertThat(response.getHeaders("Set-Cookie").get(1)).contains("Max-Age=0");
        assertThatNullPointerException().isThrownBy(() ->
            CookieUtils.addHttpOnlyCookie(null, "token", "value", 1));
        assertThatIllegalArgumentException().isThrownBy(() ->
            CookieUtils.addHttpOnlyCookie(response, " ", "value", 1));
        assertThatIllegalArgumentException().isThrownBy(() ->
            CookieUtils.addHttpOnlyCookie(response, null, "value", 1));
        assertThatIllegalArgumentException().isThrownBy(() ->
            CookieUtils.addHttpOnlyCookie(response, "token", "value", -1));
    }

    @Test
    void genericStreamAndStringUtilitiesHandleBoundaries() {
        assertThat(GenericUtils.newInstance(Empty.class)).isInstanceOf(Empty.class);
        assertThat(GenericUtils.newInstance(Empty.class, new Class<?>[0], new Object[0]))
            .isInstanceOf(Empty.class);
        assertThat(GenericUtils.newInstance(
            WithArgument.class, new Class<?>[]{String.class}, new Object[]{"value"}).value)
            .isEqualTo("value");
        assertThatIllegalArgumentException().isThrownBy(() -> GenericUtils.newInstance(null));
        assertThatIllegalArgumentException().isThrownBy(() -> GenericUtils.newInstance(NoEmpty.class));
        assertThatIllegalArgumentException().isThrownBy(() -> GenericUtils.newInstance(Throwing.class));

        assertThat(StreamUtils.ofNullable(null)).isEmpty();
        assertThat(StreamUtils.ofNullable(List.of(1))).containsExactly(1);
        assertThat(StreamUtils.valuesOfNullable(null)).isEmpty();
        assertThat(StreamUtils.valuesOfNullable(Map.of("a", 1))).containsExactly(1);
        assertThat(StreamUtils.keysOfNullable(null)).isEmpty();
        assertThat(StreamUtils.keysOfNullable(Map.of("a", 1))).containsExactly("a");
        assertThat(StreamUtils.toSupplier(List.of(1)).get()).containsExactly(1);
        var distinct = StreamUtils.<String>distinctBy(String::length);
        assertThat(distinct.test("a")).isTrue();
        assertThat(distinct.test("b")).isFalse();

        assertThat(StringUtils.isBlank(" ")).isTrue();
        assertThat(StringUtils.isNotBlank("a")).isTrue();
        assertThat(StringUtils.containsAnyNonUnicode(null)).isFalse();
        assertThat(StringUtils.containsAnyNonUnicode("abc")).isFalse();
        assertThat(StringUtils.containsAnyNonUnicode("Tiếng Việt")).isTrue();
        assertThat(StringUtils.containsIgnoreCase(null, null)).isTrue();
        assertThat(StringUtils.containsIgnoreCase(null, "a")).isFalse();
        assertThat(StringUtils.containsIgnoreCase("a", null)).isFalse();
        assertThat(StringUtils.containsIgnoreCase(" A ", "a")).isTrue();
    }

    @Test
    void httpUtilitiesAreContextSafeAndSupportBothCachingWrappers() throws Exception {
        assertThat(HttpUtils.getRequest()).isNull();
        assertThat(HttpUtils.getResponse()).isNull();
        assertThat(HttpUtils.getHeader("X-Test")).isNull();
        assertThat(HttpUtils.getRequestAttribute("attr")).isNull();
        assertThat(HttpUtils.getHeaders(null)).isEmpty();
        assertThat(HttpUtils.getQueryParams(null)).isEmpty();
        assertThat(HttpUtils.getRequestBody(new MockHttpServletRequest())).isNull();
        assertThat(HttpUtils.getResponseBody(new MockHttpServletResponse())).isNull();

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        request.addHeader("X-Test", "value");
        request.addParameter("ids", "1", "2");
        request.setAttribute("attr", 42);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, response));
        assertThat(HttpUtils.getRequest()).isSameAs(request);
        assertThat(HttpUtils.getResponse()).isSameAs(response);
        assertThat(HttpUtils.getHeader("X-Test")).isEqualTo("value");
        assertThat(HttpUtils.getRequestAttribute("attr")).isEqualTo("42");
        assertThat(HttpUtils.getRequestAttribute("missing")).isNull();
        assertThat(HttpUtils.getHeaders(null)).containsEntry("X-Test", "value");
        assertThat(HttpUtils.getHeaders(request)).containsEntry("X-Test", "value");
        assertThat(HttpUtils.getQueryParams(null)).containsEntry("ids", "1,2");
        assertThat(HttpUtils.getQueryParams(request)).containsEntry("ids", "1,2");

        MockHttpServletRequest noHeaders = new MockHttpServletRequest() {
            @Override public java.util.Enumeration<String> getHeaderNames() { return null; }
        };
        assertThat(HttpUtils.getHeaders(noHeaders)).isEmpty();
        MockHttpServletRequest nullValues = new MockHttpServletRequest() {
            @Override public Map<String, String[]> getParameterMap() {
                return java.util.Collections.singletonMap("empty", null);
            }
        };
        assertThat(HttpUtils.getQueryParams(nullValues)).isEmpty();

        MockHttpServletRequest body = new MockHttpServletRequest();
        body.setContent("body".getBytes(StandardCharsets.UTF_8));
        CachedBodyHttpServletRequestWrapper platform =
            new CachedBodyHttpServletRequestWrapper(body, 16);
        assertThat(HttpUtils.getRequestBody(platform)).isEqualTo("body");

        MockHttpServletRequest contentRequest = new MockHttpServletRequest();
        contentRequest.setContent("cached".getBytes(StandardCharsets.UTF_8));
        ContentCachingRequestWrapper spring = new ContentCachingRequestWrapper(contentRequest, 16);
        spring.getInputStream().readAllBytes();
        assertThat(HttpUtils.getRequestBody(spring)).isEqualTo("cached");

        ContentCachingResponseWrapper cachedResponse =
            new ContentCachingResponseWrapper(new MockHttpServletResponse());
        assertThat(HttpUtils.getResponseBody(cachedResponse)).isNull();
        cachedResponse.getOutputStream().write("response".getBytes(StandardCharsets.UTF_8));
        assertThat(HttpUtils.getResponseBody(cachedResponse)).isEqualTo("response");
    }

    private static void assertCryptoFailure(Runnable operation) {
        assertThatThrownBy(operation::run)
            .isInstanceOf(PlatformInfrastructureException.class)
            .satisfies(error -> assertThat(error.getMessage()).doesNotContain("secret"));
    }

    private static byte[] legacyGcm(byte[] plaintext, byte[] key) throws Exception {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
            new GCMParameterSpec(128, iv));
        byte[] encrypted = cipher.doFinal(plaintext);
        byte[] payload = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
        return payload;
    }

    static class Empty { }
    static class WithArgument {
        final String value;
        WithArgument(String value) { this.value = value; }
    }
    static class NoEmpty { NoEmpty(String value) { } }
    static class Throwing { Throwing() { throw new IllegalStateException("failure"); } }
}
