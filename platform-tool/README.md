# Platform Tool

`platform-tool` là thư viện Java 25 / Spring Boot 4 dùng chung cho Excel, Mustache/HTML, PDF, CSV, ZIP, QR, MIME inspection và digest. Repository con chứa đúng hai artifact: thư viện `platform-tool` và ứng dụng chạy được `platform-tool-demo`.

## Architecture

```text
platform-tool-demo (HTTP adapter)
        -> public *.api / *.model
        -> feature internal implementation
        -> POI, JMustache, jsoup, OpenHTMLToPDF/PDFBox, Commons CSV, ZXing, Tika Core
```

Implementation không giữ state theo request; mọi bean mặc định back off khi consumer khai báo contract tương đương. Không có MVC, storage, email, auth hoặc business domain trong library.

## Capabilities

- Excel export: typed columns, multi-sheet, merge, trusted formula, styles, freeze/filter, bounded width và SXSSF streaming.
- Excel import: `.xlsx`/`.xls`, magic bytes + parser validation, sheet selection, header mapping, row errors, fail-fast/fail-after-read và hard limits; không evaluate formula upload.
- Excel template: classpath/Spring `Resource`/caller stream, Mustache placeholders, cell/named-range update, row insertion và style preservation.
- Template/HTML: JMustache logic-light, HTML escaping mặc định, normalized logical template id và jsoup allowlist sanitizer.
- PDF: sanitized HTML to PDF, page size/orientation/margin, metadata, page counters, trusted fonts/resources and watermark. Network resource access is denied by default. Demo bundles Droid Sans under Apache-2.0 (license/notice beside the font) for Vietnamese text.
- PDF operations: merge, split, page count and watermark using bounded temporary files.
- CSV: UTF-8 streaming import/export with Commons CSV and spreadsheet formula-injection neutralization.
- ZIP: streaming creation; extraction rejects Zip Slip, overwrite, excessive entries and expanded size.
- QR: ZXing PNG/`BufferedImage`/`OutputStream`.
- File: Tika Core MIME detection plus size/extension/SHA-256; JCA SHA-256/SHA-512 digest.

## Configuration

Không có `platform.tool.*`. Dùng trực tiếp Spring-native configuration:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 25MB
  mustache:
    prefix: classpath:/templates/
    suffix: .mustache
    charset: UTF-8
```

Các limit file/row/page đặc thù operation nằm trong immutable request object. Large jobs nên chạy qua `spring.task.execution.*` ở application layer và stream tới object storage.

## Security considerations

- Formula injection: XLSX chỉ `TrustedFormula` mới gọi POI formula API; string bắt đầu bằng `=` luôn là data. CSV prefix ký tự `= + - @ TAB CR LF` bằng apostrophe.
- XSS/SSTI: JMustache escape HTML mặc định; template ID không nhận path/URL; user-editable rich HTML phải qua `HtmlSanitizer`. Không SpEL, reflection hoặc bean access.
- SSRF/XXE: PDF chặn mọi external resource ngoài explicit `memory:/`, từ chối DTD/entity/XInclude và không bật SVG/XML extension parser.
- Zip bomb/Zip Slip: bounded expanded bytes/entry count, normalized child path, no overwrite/symlink destination.
- Upload: không tin filename/MIME; Excel kiểm tra extension, magic bytes và actual parse; macro formats bị từ chối; formula không evaluate.
- Resource exhaustion: streaming APIs, bounded file/HTML/row/column/sheet/page limits, SXSSF temp disposal và PDF temp cleanup.
- Demo endpoint chưa phải production security boundary. Production phải authorization riêng cho import, merge, template management và large export.

## Build and run

```bash
./mvnw -pl platform-tool -am clean package -DskipTests
java -jar platform-tool/platform-tool-demo/target/platform-tool-demo-1.0.0-SNAPSHOT.jar
```

## Curl

```bash
curl -f -X POST http://localhost:8080/api/tools/excel/export -o customer-report.xlsx
curl -f -X POST http://localhost:8080/api/tools/excel/export/template -o customer-template.xlsx
curl -f -F file=@customer-report.xlsx http://localhost:8080/api/tools/excel/import
curl -f -X POST -H 'Content-Type: application/json' -d '{"customerName":"Nguyễn Văn An"}' http://localhost:8080/api/tools/template/render
curl -f -X POST -H 'Content-Type: application/json' -d '{}' http://localhost:8080/api/tools/pdf/export -o customer-report.pdf
curl -f -F files=@a.pdf -F files=@b.pdf http://localhost:8080/api/tools/pdf/merge -o merged.pdf
curl -f -X POST http://localhost:8080/api/tools/csv/export -o customers.csv
curl -f -F file=@customers.csv http://localhost:8080/api/tools/csv/import
curl -f -X POST -H 'Content-Type: application/json' -d '{"readme.txt":"hello"}' http://localhost:8080/api/tools/archive/zip -o reports.zip
curl -f -X POST --data-urlencode 'content=https://example.invalid/report/123' http://localhost:8080/api/tools/qrcode -o qrcode.png
curl -f -F file=@customer-report.xlsx http://localhost:8080/api/tools/file/inspect
curl -f -F file=@customer-report.xlsx 'http://localhost:8080/api/tools/file/digest?algorithm=SHA_256'
```
