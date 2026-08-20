package com.company.platform.tool.autoconfigure;

import com.company.platform.tool.excel.api.ExcelExportService;
import com.company.platform.tool.excel.api.ExcelImportService;
import com.company.platform.tool.excel.api.ExcelTemplateService;
import com.company.platform.tool.excel.internal.PoiExcelExportService;
import com.company.platform.tool.excel.internal.PoiExcelImportService;
import com.company.platform.tool.excel.internal.PoiExcelTemplateService;
import com.samskivert.mustache.Mustache;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import io.micrometer.observation.ObservationRegistry;

@AutoConfiguration
@AutoConfigureAfter(PlatformToolTemplateAutoConfiguration.class)
@ConditionalOnClass(Workbook.class)
public class PlatformToolExcelAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    ExcelExportService platformToolExcelExportService(ObjectProvider<ObservationRegistry> registries) {
        return new PoiExcelExportService(registries.getIfAvailable(() -> ObservationRegistry.NOOP));
    }

    @Bean
    @ConditionalOnMissingBean
    ExcelImportService platformToolExcelImportService(ObjectProvider<ObservationRegistry> registries) {
        return new PoiExcelImportService(registries.getIfAvailable(() -> ObservationRegistry.NOOP));
    }

    @Bean
    @ConditionalOnClass(Mustache.class)
    @ConditionalOnMissingBean
    ExcelTemplateService platformToolExcelTemplateService(Mustache.Compiler compiler) {
        return new PoiExcelTemplateService(compiler);
    }
}
