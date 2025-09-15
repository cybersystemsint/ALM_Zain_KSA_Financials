package com.telkom.co.ke.almoptics.serviceImplementor;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// Reactor imports
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

// Repository and entity imports
import com.telkom.co.ke.almoptics.entities.tb_FarReport;
import com.telkom.co.ke.almoptics.repository.FarReportRepository;

// FastExcel imports
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

// Standard Java imports
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.PreDestroy;

@Service
public class OptimizedFarReportReactiveService {

    private static final Logger LOGGER = LogManager.getLogger(OptimizedFarReportReactiveService.class);

    @Autowired
    private FarReportRepository repository;

    // Dedicated thread pool for database operations
    private final Scheduler databaseScheduler = Schedulers.newBoundedElastic(
            Runtime.getRuntime().availableProcessors() * 2,
            Integer.MAX_VALUE,
            "db-export",
            60,
            true
    );

    private static final String[] EXPECTED_FIELDS = {
            "recordDatetime", "book", "assetId", "quantity", "description", "assetType", "creationDate",
            "serialNumber", "tagNumber", "picStatus", "picDate", "cipDeliveryDate", "linkId", "acceptanceNumber",
            "depreciateFlag", "cipEu", "invoiceNumber", "poNumber", "poLineNumber", "uplLine", "transferToNewFar",
            "assetStatus", "value", "partNumber", "vendorName", "vendorNumber", "mergedCode", "costAccount",
            "accumulatedDepreAccount", "cipCostAccount", "expenseCostCenter", "expenseAccount", "life",
            "datePlacedInService", "cost", "nbv", "depreciationAmount", "ytdDepreciation", "depreciationReserve",
            "salvageValue", "category", "categoryDescription", "locationSegment1", "locationSegment2",
            "locationSegment3", "locationSegment4", "locations", "sequenceNumber", "createdBy", "createdDate",
            "updatedBy", "updatedDate", "monthlyDepreciationAmt", "accumulatedDepreciationAmt", "depreciationDate",
            "netCost", "statusFlag", "changedBy", "insertedBy", "financialApproval", "changedDate", "nodeType"
    };

    public Flux<DataBuffer> exportToExcelReactive(String column, String value, String operator) {
        return Mono.fromCallable(() -> createExcelStream(column, value, operator))
                .subscribeOn(databaseScheduler)
                .flatMapMany(stream -> stream)
                .onBackpressureBuffer(500) // Fixed: removed the lambda that was causing compilation error
                .doOnCancel(() -> LOGGER.info("Client cancelled the export"))
                .doOnComplete(() -> LOGGER.info("Export stream completed"))
                .doOnError(error -> LOGGER.error("Error in export stream", error));
    }

    private Flux<DataBuffer> createExcelStream(String column, String value, String operator) {
        return Flux.create(sink -> {
            AtomicBoolean isComplete = new AtomicBoolean(false);

            try {
                // Create pipe with larger buffer for better throughput
                PipedInputStream inputStream = new PipedInputStream(128 * 1024); // 128KB
                PipedOutputStream outputStream = new PipedOutputStream(inputStream);

                // Start Excel generation in background
                CompletableFuture.runAsync(() -> {
                    try {
                        generateExcelOptimized(outputStream, column, value, operator);
                    } catch (Exception e) {
                        LOGGER.error("Error generating Excel", e);
                        if (!isComplete.get()) {
                            sink.error(e);
                        }
                    } finally {
                        try {
                            outputStream.close();
                        } catch (IOException ignored) {}
                        isComplete.set(true);
                    }
                });

                // Stream data from pipe
                streamDataFromPipe(inputStream, sink, isComplete);

            } catch (Exception e) {
                sink.error(e);
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private void streamDataFromPipe(PipedInputStream inputStream, FluxSink<DataBuffer> sink, AtomicBoolean isComplete) {
        CompletableFuture.runAsync(() -> {
            byte[] buffer = new byte[16384]; // 16KB chunks for optimal network transmission
            DefaultDataBufferFactory dataBufferFactory = new DefaultDataBufferFactory();

            try {
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    if (sink.isCancelled()) {
                        LOGGER.info("Stream cancelled, stopping data transmission");
                        break;
                    }

                    // Create data buffer - use correct Spring DataBuffer
                    byte[] data = Arrays.copyOf(buffer, bytesRead);
                    DataBuffer dataBuffer = dataBufferFactory.wrap(data);
                    sink.next(dataBuffer);

                    // Small delay to prevent overwhelming slow clients
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                // Wait a bit for Excel generation to complete
                int attempts = 0;
                while (!isComplete.get() && attempts < 100) {
                    try {
                        Thread.sleep(50);
                        attempts++;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                sink.complete();

            } catch (IOException e) {
                if (!sink.isCancelled()) {
                    sink.error(e);
                }
            } finally {
                try {
                    inputStream.close();
                } catch (IOException ignored) {}
            }
        });
    }

    private void generateExcelOptimized(OutputStream outputStream, String column, String value, String operator) throws IOException {
        Specification<tb_FarReport> spec = createSpecificationFixed(column, value, operator);

        long total = repository.count(spec);
        LOGGER.info("Starting export of {} records", total);

        int batchSize = 5000; // Smaller batches for better memory management
        int pages = (int) ((total + batchSize - 1) / batchSize);

        try (BufferedOutputStream bos = new BufferedOutputStream(outputStream, 64 * 1024)) {
            Workbook workbook = new Workbook(bos, "Far Reports", "1.0");
            Worksheet sheet = workbook.newWorksheet("Sheet1");

            // Write header
            writeHeader(sheet);

            AtomicInteger currentRow = new AtomicInteger(1); // Thread-safe row counter

            // Process batches sequentially for now (simpler approach)
            for (int page = 0; page < pages; page++) {
                Pageable pageable = PageRequest.of(page, batchSize, Sort.by("recordNo").ascending());

                long startTime = System.currentTimeMillis();
                Page<tb_FarReport> batchPage = repository.findAll(spec, pageable);
                List<tb_FarReport> content = batchPage.getContent();
                long fetchTime = System.currentTimeMillis() - startTime;

                writeBatchToSheetOptimized(sheet, content, currentRow);
                sheet.flush();

                LOGGER.info("Processed batch {} / {} in {} ms ({} records)",
                        page + 1, pages, fetchTime, content.size());
            }

            workbook.finish();
            bos.flush();

            LOGGER.info("Excel generation completed successfully");
        }
    }

    private void writeHeader(Worksheet sheet) {
        for (int col = 0; col < EXPECTED_FIELDS.length; col++) {
            sheet.value(0, col, EXPECTED_FIELDS[col]);
        }
    }

    private void writeBatchToSheetOptimized(Worksheet sheet, List<tb_FarReport> batch, AtomicInteger currentRow) {
        for (tb_FarReport entity : batch) {
            int row = currentRow.getAndIncrement();
            BeanWrapper beanWrapper = new BeanWrapperImpl(entity);

            for (int col = 0; col < EXPECTED_FIELDS.length; col++) {
                String field = EXPECTED_FIELDS[col];
                Object val = beanWrapper.getPropertyValue(field);

                if (val == null) {
                    sheet.value(row, col, "");
                } else if (val instanceof java.util.Date) {
                    sheet.value(row, col, (java.util.Date) val);
                } else if (val instanceof Number) {
                    sheet.value(row, col, ((Number) val).doubleValue());
                } else if (val instanceof Boolean) {
                    sheet.value(row, col, (Boolean) val);
                } else {
                    sheet.value(row, col, val.toString());
                }
            }
        }
    }

    public void exportToExcelDirect(OutputStream outputStream, String column, String value, String operator) throws IOException {
        Specification<tb_FarReport> spec = createSpecificationFixed(column, value, operator);

        long total = repository.count(spec);
        LOGGER.info("Starting direct export of {} records", total);

        int batchSize = 2000; // Smaller batches for real streaming
        int pages = (int) ((total + batchSize - 1) / batchSize);

        try (BufferedOutputStream bos = new BufferedOutputStream(outputStream, 16 * 1024)) {
            Workbook workbook = new Workbook(bos, "Far Reports", "1.0");
            Worksheet sheet = workbook.newWorksheet("Sheet1");

            // Write header
            writeHeader(sheet);

            int currentRow = 1;

            // Process and flush each batch immediately
            for (int page = 0; page < pages; page++) {
                long startTime = System.currentTimeMillis();

                Pageable pageable = PageRequest.of(page, batchSize, Sort.by("recordNo").ascending());
                Page<tb_FarReport> batchPage = repository.findAll(spec, pageable);
                List<tb_FarReport> content = batchPage.getContent();

                // Write batch to sheet
                currentRow = writeBatchToSheetDirect(sheet, content, currentRow);

                // CRITICAL: Flush every single batch for true streaming
                sheet.flush();
                bos.flush();

                long timeTaken = System.currentTimeMillis() - startTime;
                LOGGER.info("Streamed batch {} / {} in {} ms ({} records)",
                        page + 1, pages, timeTaken, content.size());

                // Small delay to prevent overwhelming database
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            workbook.finish();
            bos.flush();

            LOGGER.info("Direct streaming export completed successfully with {} records", total);
        }
    }

    public void exportToExcelWithLimit(OutputStream outputStream, String column, String value, String operator, int limit) throws IOException {
        Specification<tb_FarReport> spec = createSpecificationFixed(column, value, operator);

        LOGGER.info("Starting limited export with max {} records", limit);

        int batchSize = 2000;
        int pages = (limit + batchSize - 1) / batchSize;

        try (BufferedOutputStream bos = new BufferedOutputStream(outputStream, 16 * 1024)) {
            Workbook workbook = new Workbook(bos, "Far Reports", "1.0");
            Worksheet sheet = workbook.newWorksheet("Sheet1"); // Fixed: was newWorkbook

            writeHeader(sheet);

            int currentRow = 1;
            int totalProcessed = 0;

            for (int page = 0; page < pages && totalProcessed < limit; page++) {
                int remainingRecords = limit - totalProcessed;
                int currentBatchSize = Math.min(batchSize, remainingRecords);

                Pageable pageable = PageRequest.of(page, currentBatchSize, Sort.by("recordNo").ascending());
                Page<tb_FarReport> batchPage = repository.findAll(spec, pageable);
                List<tb_FarReport> content = batchPage.getContent();

                if (content.isEmpty()) break;

                currentRow = writeBatchToSheetDirect(sheet, content, currentRow);
                totalProcessed += content.size();

                // Flush every batch
                sheet.flush();
                bos.flush();

                LOGGER.info("Limited export: processed {} / {} records", totalProcessed, limit);

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            workbook.finish();
            bos.flush();

            LOGGER.info("Limited export completed with {} records", totalProcessed);
        }
    }
    private int writeBatchToSheetDirect(Worksheet sheet, List<tb_FarReport> batch, int startRow) {
        int currentRow = startRow;

        for (tb_FarReport entity : batch) {
            BeanWrapper beanWrapper = new BeanWrapperImpl(entity);

            for (int col = 0; col < EXPECTED_FIELDS.length; col++) {
                String field = EXPECTED_FIELDS[col];
                Object val = beanWrapper.getPropertyValue(field);

                if (val == null) {
                    sheet.value(currentRow, col, "");
                } else if (val instanceof java.util.Date) {
                    sheet.value(currentRow, col, (java.util.Date) val);
                } else if (val instanceof Number) {
                    sheet.value(currentRow, col, ((Number) val).doubleValue());
                } else if (val instanceof Boolean) {
                    sheet.value(currentRow, col, (Boolean) val);
                } else {
                    sheet.value(currentRow, col, val.toString());
                }
            }
            currentRow++;
        }

        return currentRow;
    }
    private Specification<tb_FarReport> createSpecificationFixed(String column, String value, String operator) {
        return (root, query, cb) -> {
            if (StringUtils.isEmpty(column) || StringUtils.isEmpty(value)) {
                return cb.isTrue(cb.literal(true));
            }

            // Fix field mapping for snake_case to camelCase
            String normalizedColumn = column;
            if ("asset_type".equals(column)) {
                normalizedColumn = "assetType";
            }

            if (!Arrays.asList(EXPECTED_FIELDS).contains(normalizedColumn)) {
                throw new IllegalArgumentException("Invalid column name: " + column);
            }

            switch (operator.toLowerCase()) {
                case "equals":
                    return cb.equal(root.get(normalizedColumn), value);
                case "like":
                    return cb.like(cb.lower(root.get(normalizedColumn)), "%" + value.toLowerCase() + "%");
                default:
                    throw new IllegalArgumentException("Unsupported operator: " + operator);
            }
        };
    }

    @PreDestroy
    public void cleanup() {
        databaseScheduler.dispose();
        LOGGER.info("Reactive service cleanup completed");
    }
}