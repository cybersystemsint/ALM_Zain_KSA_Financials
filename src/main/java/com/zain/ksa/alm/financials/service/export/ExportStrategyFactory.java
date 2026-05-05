package com.zain.ksa.alm.financials.service.export;

import com.zain.ksa.alm.financials.dto.request.ExportFormat;
import org.springframework.stereotype.Component;

/**
 * Factory that resolves the correct ExportStrategy for a requested format.
 * Open/Closed: new formats only require a new strategy bean, not a factory change.
 *
 * NOTE: Generic <T> is intentionally not on the class — Spring injects beans
 * by raw type. The cast at resolve() is safe because strategies are stateless
 * and work on any DTO type via reflection.
 */
@Component
public class ExportStrategyFactory {

    private final CsvExportStrategy<?>   csvStrategy;
    private final ExcelExportStrategy<?> excelStrategy;

    // Explicit constructor — @RequiredArgsConstructor + generic beans causes
    // Spring injection failure at startup. Raw wildcard <?> resolves it.
    public ExportStrategyFactory(CsvExportStrategy<?> csvStrategy,
                                  ExcelExportStrategy<?> excelStrategy) {
        this.csvStrategy   = csvStrategy;
        this.excelStrategy = excelStrategy;
    }

    @SuppressWarnings("unchecked")
    public <T> ExportStrategy<T> resolve(ExportFormat format) {
        return switch (format) {
            case CSV   -> (ExportStrategy<T>) csvStrategy;
            case EXCEL -> (ExportStrategy<T>) excelStrategy;
        };
    }
}