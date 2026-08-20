package com.company.platform.tool.autoconfigure;

import com.company.platform.tool.pdf.api.PdfExportService;
import com.company.platform.tool.pdf.internal.OpenHtmlPdfExportService;
import com.company.platform.tool.pdfops.api.PdfOperationService;
import com.company.platform.tool.pdfops.internal.PdfBoxOperationService;
import com.company.platform.tool.template.api.HtmlSanitizer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import io.micrometer.observation.ObservationRegistry;

@AutoConfiguration
@AutoConfigureAfter(PlatformToolTemplateAutoConfiguration.class)
@ConditionalOnClass({PdfRendererBuilder.class, PDDocument.class})
public class PlatformToolPdfAutoConfiguration {
    @Bean
    @ConditionalOnBean(HtmlSanitizer.class)
    @ConditionalOnMissingBean
    PdfExportService platformToolPdfExportService(HtmlSanitizer sanitizer, ObjectProvider<ObservationRegistry> registries) {
        return new OpenHtmlPdfExportService(sanitizer, registries.getIfAvailable(() -> ObservationRegistry.NOOP));
    }

    @Bean
    @ConditionalOnMissingBean
    PdfOperationService platformToolPdfOperationService() {
        return new PdfBoxOperationService();
    }
}
