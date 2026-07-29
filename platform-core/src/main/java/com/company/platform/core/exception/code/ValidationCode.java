
package com.company.platform.core.exception.code;

import com.company.platform.core.i18n.I18nKey;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum ValidationCode implements I18nKey {

    /*
     * Platform validation messages
     */
    FAILED("error.validation.failed"),
    CONSTRAINT_VIOLATION("error.validation.constraint-violation"),

    JSON_MALFORMED("error.validation.json-malformed"),
    REQUEST_DEFINITION_INVALID("error.validation.request-definition-invalid"),
    FIELD_INVALID("error.validation.field-invalid"),
    FIELD_REQUIRED("error.validation.field-required"),
    FIELD_UNKNOWN("error.validation.field-unknown"),
    FIELD_TYPE("error.validation.field-type"),
    FIELD_BOOLEAN("error.validation.field-boolean"),
    FIELD_INTEGER("error.validation.field-integer"),
    FIELD_NUMBER("error.validation.field-number"),
    FIELD_UUID("error.validation.field-uuid"),
    FIELD_FORMAT("error.validation.field-format"),
    FIELD_LENGTH("error.validation.field-length"),
    FIELD_MIN_LENGTH("error.validation.field-min-length"),
    FIELD_MAX_LENGTH("error.validation.field-max-length"),
    FIELD_MIN_VALUE("error.validation.field-min-value"),
    FIELD_MAX_VALUE("error.validation.field-max-value"),
    FIELD_RANGE("error.validation.field-range"),
    FIELD_POSITIVE("error.validation.field-positive"),
    FIELD_POSITIVE_OR_ZERO("error.validation.field-positive-or-zero"),
    FIELD_NEGATIVE("error.validation.field-negative"),
    FIELD_NEGATIVE_OR_ZERO("error.validation.field-negative-or-zero"),
    FIELD_EMAIL("error.validation.field-email"),
    FIELD_PHONE("error.validation.field-phone"),
    FIELD_DATE("error.validation.field-date"),
    FIELD_DATE_TIME("error.validation.field-date-time"),
    FIELD_ENUM("error.validation.field-enum"),
    FIELD_PATTERN("error.validation.field-pattern"),

    FIELDS_NOT_MATCH("error.validation.fields-not-match"),

    INVALID_PAGINATION("error.validation.invalid-pagination"),
    INVALID_SORT("error.validation.invalid-sort"),
    INVALID_FILTER("error.validation.invalid-filter"),
    INVALID_ENUM("error.enum.invalid"),

    /*
     * Jakarta Bean Validation constraints
     */
    ASSERT_FALSE(
        "jakarta.validation.constraints.AssertFalse.message"
    ),
    ASSERT_TRUE(
        "jakarta.validation.constraints.AssertTrue.message"
    ),
    DECIMAL_MAX(
        "jakarta.validation.constraints.DecimalMax.message"
    ),
    DECIMAL_MIN(
        "jakarta.validation.constraints.DecimalMin.message"
    ),
    DIGITS(
        "jakarta.validation.constraints.Digits.message"
    ),
    EMAIL(
        "jakarta.validation.constraints.Email.message"
    ),
    FUTURE(
        "jakarta.validation.constraints.Future.message"
    ),
    FUTURE_OR_PRESENT(
        "jakarta.validation.constraints.FutureOrPresent.message"
    ),
    MAX(
        "jakarta.validation.constraints.Max.message"
    ),
    MIN(
        "jakarta.validation.constraints.Min.message"
    ),
    NEGATIVE(
        "jakarta.validation.constraints.Negative.message"
    ),
    NEGATIVE_OR_ZERO(
        "jakarta.validation.constraints.NegativeOrZero.message"
    ),
    NOT_BLANK(
        "jakarta.validation.constraints.NotBlank.message"
    ),
    NOT_EMPTY(
        "jakarta.validation.constraints.NotEmpty.message"
    ),
    NOT_NULL(
        "jakarta.validation.constraints.NotNull.message"
    ),
    NULL(
        "jakarta.validation.constraints.Null.message"
    ),
    PAST(
        "jakarta.validation.constraints.Past.message"
    ),
    PAST_OR_PRESENT(
        "jakarta.validation.constraints.PastOrPresent.message"
    ),
    PATTERN(
        "jakarta.validation.constraints.Pattern.message"
    ),
    POSITIVE(
        "jakarta.validation.constraints.Positive.message"
    ),
    POSITIVE_OR_ZERO(
        "jakarta.validation.constraints.PositiveOrZero.message"
    ),
    SIZE(
        "jakarta.validation.constraints.Size.message"
    ),

    /*
     * Hibernate Validator constraints
     */
    CREDIT_CARD_NUMBER(
        "org.hibernate.validator.constraints.CreditCardNumber.message"
    ),
    CURRENCY(
        "org.hibernate.validator.constraints.Currency.message"
    ),
    EAN(
        "org.hibernate.validator.constraints.EAN.message"
    ),
    IP_ADDRESS(
        "org.hibernate.validator.constraints.IpAddress.message"
    ),
    ISBN(
        "org.hibernate.validator.constraints.ISBN.message"
    ),
    LENGTH(
        "org.hibernate.validator.constraints.Length.message"
    ),
    CODE_POINT_LENGTH(
        "org.hibernate.validator.constraints.CodePointLength.message"
    ),
    LUHN_CHECK(
        "org.hibernate.validator.constraints.LuhnCheck.message"
    ),
    MOD_10_CHECK(
        "org.hibernate.validator.constraints.Mod10Check.message"
    ),
    MOD_11_CHECK(
        "org.hibernate.validator.constraints.Mod11Check.message"
    ),
    NORMALIZED(
        "org.hibernate.validator.constraints.Normalized.message"
    ),
    PARAMETERS_SCRIPT_ASSERT(
        "org.hibernate.validator.constraints.ParametersScriptAssert.message"
    ),
    RANGE(
        "org.hibernate.validator.constraints.Range.message"
    ),
    SCRIPT_ASSERT(
        "org.hibernate.validator.constraints.ScriptAssert.message"
    ),
    UNIQUE_ELEMENTS(
        "org.hibernate.validator.constraints.UniqueElements.message"
    ),
    URL(
        "org.hibernate.validator.constraints.URL.message"
    ),
    UUID(
        "org.hibernate.validator.constraints.UUID.message"
    ),

    /*
     * Country-specific Hibernate Validator constraints
     */
    BRAZIL_CNPJ(
        "org.hibernate.validator.constraints.br.CNPJ.message"
    ),
    BRAZIL_CPF(
        "org.hibernate.validator.constraints.br.CPF.message"
    ),
    BRAZIL_TITULO_ELEITORAL(
        "org.hibernate.validator.constraints.br.TituloEleitoral.message"
    ),
    KOREAN_RESIDENT_REGISTRATION_NUMBER(
        "org.hibernate.validator.constraints.kor.KorRRN.message"
    ),
    POLISH_REGON(
        "org.hibernate.validator.constraints.pl.REGON.message"
    ),
    POLISH_NIP(
        "org.hibernate.validator.constraints.pl.NIP.message"
    ),
    POLISH_PESEL(
        "org.hibernate.validator.constraints.pl.PESEL.message"
    ),
    RUSSIAN_INN(
        "org.hibernate.validator.constraints.ru.INN.message"
    ),

    /*
     * Duration constraints
     */
    DURATION_MAX(
        "org.hibernate.validator.constraints.time.DurationMax.message"
    ),
    DURATION_MIN(
        "org.hibernate.validator.constraints.time.DurationMin.message"
    ),

    /*
     * Bitcoin address validation
     */
    BITCOIN_ADDRESS_SINGLE(
        "org.hibernate.validator.constraints.BitcoinAddress.message.single"
    ),
    BITCOIN_ADDRESS_MULTIPLE(
        "org.hibernate.validator.constraints.BitcoinAddress.message.multiple"
    ),

    BITCOIN_TYPE_P2PKH(
        "org.hibernate.validator.constraints.BitcoinAddress.type.p2pkh"
    ),
    BITCOIN_TYPE_P2SH(
        "org.hibernate.validator.constraints.BitcoinAddress.type.p2sh"
    ),
    BITCOIN_TYPE_BECH32(
        "org.hibernate.validator.constraints.BitcoinAddress.type.bech32"
    ),
    BITCOIN_TYPE_P2WSH(
        "org.hibernate.validator.constraints.BitcoinAddress.type.p2wsh"
    ),
    BITCOIN_TYPE_P2WPKH(
        "org.hibernate.validator.constraints.BitcoinAddress.type.p2wpkh"
    ),
    BITCOIN_TYPE_P2TR(
        "org.hibernate.validator.constraints.BitcoinAddress.type.p2tr"
    );

    String key;
}
