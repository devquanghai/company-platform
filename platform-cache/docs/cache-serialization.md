# Cache serialization

## Định dạng mặc định

- Key: `STRING`.
- Value: strict JSON qua `JsonMapperHelper` của `platform-core`.
- Envelope: bật, có schema ID/version, negative marker và freshness metadata.
- Java native serialization: cấm.

Module không tạo `ObjectMapper` riêng và không bật unsafe default typing.
Application phải giữ Jackson coercion strict để `"1"`, `"true"` hoặc ngày sai
format không âm thầm đổi kiểu.

## Envelope

Envelope tách payload ổn định khỏi metadata thay đổi:

```json
{
  "schemaId": "customer-profile",
  "schemaVersion": 2,
  "entryVersion": 7,
  "negative": false,
  "freshUntil": "2026-07-30T10:00:00Z",
  "staleUntil": "2026-07-30T10:05:00Z",
  "payload": {}
}
```

CAS so sánh canonical payload bytes cùng schema/negative marker, không serialize
lại toàn envelope có timestamp rồi so sánh.

## Type safety

Typed cache/facade nhận `Class<T>` hoặc `CacheType<T>`. Không deserialize type
do untrusted payload tự khai báo. Nếu cần polymorphism, `trusted-packages` phải
là allowlist tối thiểu và được review bảo mật.

## Schema evolution

- Thay đổi additive, backward-compatible: giữ schema version nếu reader cũ chấp
  nhận được.
- Thay đổi breaking: tăng schema version hoặc key version.
- Reader có thể hỗ trợ một cửa sổ version rõ ràng; version không hỗ trợ là miss
  hoặc failure theo policy, không ép kiểu.
- Deploy reader tương thích trước writer mới.

## Giới hạn và lỗi

Payload sau serialize không vượt `maximum-entry-size`. Không log raw payload,
credential hoặc exception message có dữ liệu người dùng. Serialization failure
không retry và phải xuất metric/event đã sanitize.

