# Platform Base

## Tổng quan

`platform-base` là Maven reactor chứa parent POM cấp công ty và năm module nền tảng. Project dùng Java 25, Spring Boot BOM 4.0.7 và Maven Wrapper 3.9.11. Parent chỉ quản lý chính sách build; nó không chứa business logic hay ép dependency runtime xuống module con.

## Vai trò của `platform-parent`

- **Parent POM**: module con kế thừa properties, dependency management và plugin conventions.
- **Aggregator POM**: danh sách `<modules>` cho phép build toàn reactor bằng một lệnh.
- **BOM**: chỉ quản lý dependency versions. Root import `spring-boot-dependencies`; root không kế thừa `spring-boot-starter-parent` và bản thân root không phải BOM thuần.

## Cấu trúc

```text
platform-base/
├── pom.xml
├── platform-core/pom.xml
├── platform-security/pom.xml
├── platform-cache/pom.xml
├── platform-queue/pom.xml
├── platform-service-exchange/pom.xml
├── .mvn/wrapper/maven-wrapper.properties
├── .mvn/jvm.config
├── mvnw
├── mvnw.cmd
├── .editorconfig
├── .gitignore
└── README.md
```

## Yêu cầu môi trường

- JDK 25 (Maven Enforcer chấp nhận `[25,26)`).
- `curl` hoặc `wget`, và `unzip` trong lần đầu Wrapper tải Maven.
- Không cần cài Maven global.

```bash
java -version
./mvnw -version
```

## Build và kiểm tra

```bash
./mvnw clean verify
./mvnw clean verify -Pci
./mvnw clean verify -pl platform-core -am
./mvnw clean install
./mvnw help:effective-pom -pl platform-core
./mvnw dependency:tree -pl platform-core
```

Repository không duy trì unit test hoặc JaCoCo. Integration test theo `*IT`, `*ITCase`, `*IntegrationTest` nằm trong `platform-integration-test` và chạy qua Failsafe.

`-Plocal` tắt deploy. `-Pci` chạy integration test và dependency convergence. `-Prelease` gắn source/Javadoc JAR nhưng không tự deploy. Signing chỉ bật khi thêm `-Dgpg.sign=true`.

## Kế thừa và mở rộng

Module con khai báo parent bằng `relativePath`:

```xml
<parent>
    <groupId>com.company.platform</groupId>
    <artifactId>platform-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
</parent>
```

Để thêm module: tạo thư mục và POM kế thừa như trên, sau đó thêm tên thư mục vào `<modules>`. Dependency đã thuộc Spring Boot BOM được khai báo không có version. Dependency mới ngoài BOM phải có version property cố định và entry trong root `dependencyManagement`. Plugin mới phải có version property và cấu hình trong `pluginManagement`; chỉ thêm vào `build/plugins` khi thực sự phải chạy trên mọi module.

Module có thể override property hoặc cấu hình plugin trong POM riêng. Application muốn executable JAR phải tự khai báo `spring-boot-maven-plugin`; parent không bind `repackage`. Không đặt starter/runtime dependency trong root `<dependencies>`.

## Nexus và deploy

Khai báo endpoint thực tế bằng `distributionManagement` trong POM công ty hoặc một deployment profile được quản trị; không commit credential. Ví dụ URL placeholder cần thay:

```xml
<distributionManagement>
    <repository><id>company-releases</id><url>https://nexus.example.com/repository/maven-releases/</url></repository>
    <snapshotRepository><id>company-snapshots</id><url>https://nexus.example.com/repository/maven-snapshots/</url></snapshotRepository>
</distributionManagement>
```

Credential nằm trong `~/.m2/settings.xml` và nhận từ biến môi trường:

```xml
<settings>
    <servers>
        <server><id>company-releases</id><username>${env.NEXUS_USERNAME}</username><password>${env.NEXUS_PASSWORD}</password></server>
        <server><id>company-snapshots</id><username>${env.NEXUS_USERNAME}</username><password>${env.NEXUS_PASSWORD}</password></server>
    </servers>
</settings>
```

Sau khi cấu hình endpoint và credential:

```bash
./mvnw clean deploy -Prelease
# Thêm -Dgpg.sign=true nếu repository yêu cầu chữ ký.
```

Parent phải được publish nếu repository khác cần kế thừa. Trong cùng reactor, `relativePath` đủ để Maven tìm parent. Library dùng độc lập cũng phải deploy; service application thường không cần publish như library.

## Lỗi phổ biến

- **Enforcer báo sai Java**: trỏ `JAVA_HOME` tới JDK 25 rồi chạy lại `./mvnw -version`.
- **Không tải được Wrapper/dependency**: kiểm tra proxy, DNS, TLS và Maven mirror trong `settings.xml`.
- **Dependency convergence fail ở CI**: xem `dependency:tree`, ưu tiên BOM; chỉ override version khi có lý do và comment rõ.
- **Deploy thiếu repository/401**: kiểm tra `distributionManagement`, server ID khớp `settings.xml`, và biến môi trường Nexus.
- **Application không executable**: khai báo `spring-boot-maven-plugin` trong chính module service.
