package com.company.platform.tool.autoconfigure;

import com.company.platform.tool.archive.api.ArchiveService;
import com.company.platform.tool.archive.internal.SecureZipArchiveService;
import com.company.platform.tool.csv.api.CsvExportService;
import com.company.platform.tool.csv.api.CsvImportService;
import com.company.platform.tool.csv.internal.CommonsCsvService;
import com.company.platform.tool.file.api.DigestService;
import com.company.platform.tool.file.api.FileInspectionService;
import com.company.platform.tool.file.internal.JcaDigestService;
import com.company.platform.tool.file.internal.TikaFileInspectionService;
import com.company.platform.tool.qrcode.api.QrCodeService;
import com.company.platform.tool.qrcode.internal.ZxingQrCodeService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class PlatformToolFileAutoConfiguration {
    @Bean
    @ConditionalOnClass(org.apache.commons.csv.CSVFormat.class)
    @ConditionalOnMissingBean({CsvExportService.class, CsvImportService.class})
    CommonsCsvService platformToolCsvService() {
        return new CommonsCsvService();
    }

    @Bean
    @ConditionalOnMissingBean
    ArchiveService platformToolArchiveService() {
        return new SecureZipArchiveService();
    }

    @Bean
    @ConditionalOnMissingBean
    DigestService platformToolDigestService() {
        return new JcaDigestService();
    }

    @Bean
    @ConditionalOnClass(org.apache.tika.Tika.class)
    @ConditionalOnMissingBean
    FileInspectionService platformToolFileInspectionService() {
        return new TikaFileInspectionService();
    }

    @Bean
    @ConditionalOnClass(com.google.zxing.qrcode.QRCodeWriter.class)
    @ConditionalOnMissingBean
    QrCodeService platformToolQrCodeService() {
        return new ZxingQrCodeService();
    }
}
