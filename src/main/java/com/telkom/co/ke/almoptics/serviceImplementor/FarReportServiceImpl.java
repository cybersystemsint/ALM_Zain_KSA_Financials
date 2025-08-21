/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.serviceImplementor;

import com.telkom.co.ke.almoptics.entities.tb_FarReport;
import com.telkom.co.ke.almoptics.repository.FarReportRepo;
import com.telkom.co.ke.almoptics.repository.FarReportRepository;
import com.telkom.co.ke.almoptics.services.FarReportService;
//import java.awt.print.Pageable;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import javax.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Pageable;

/**
 *
 * @author jgithu
 */

@Service
@Transactional
public class FarReportServiceImpl implements FarReportService {
    @Autowired
    private FarReportRepository farReportRepository;
    @Autowired
    private FarReportRepo farRepo;

    @Autowired
    private JdbcTemplate jdbcTemplate;


    @Override
    public Page<tb_FarReport> findAll(Pageable pageable) {
        return this.farRepo.findAll(pageable);
    }

    @Override
    public tb_FarReport save(tb_FarReport boards) {
        return (tb_FarReport) this.farRepo.save(boards);
    }

    @Override
    public List<tb_FarReport> findByAssetId(String paramString) {
        return this.farRepo.findByAssetId(paramString);

    }

    @Override
    public List<tb_FarReport> findByInventoryStatus(String paramString) {
        return this.farRepo.findByInventoryStatus(paramString);

    }

    public List<tb_FarReport> findAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<tb_FarReport> pageResult = farReportRepository.findAll(pageable);
        return pageResult.getContent();
    }

    public long count() {
        return farReportRepository.count();
    }

    // Hardcoded columns in order (from db)

    private static final String[] COLUMNS = {
            "recordNo", "recordDatetime", "book", "assetId", "quantity", "description", "asset_type", "creationDate",
            "serialNumber", "tagNumber", "picStatus", "picDate", "cipDeliveryDate", "linkId", "acceptanceNumber",
            "depreciateFlag", "cipEu", "invoiceNumber", "poNumber", "poLineNumber", "uplLine", "transferToNewFar",
            "assetStatus", "value", "partNumber", "vendorName", "vendorNumber", "mergedCode", "costAccount",
            "accumulatedDepreAccount", "cipCostAccount", "expenseCostCenter", "expenseAccount", "Life",
            "datePlacedInService", "cost", "nbv", "depreciationAmount", "ytdDepreciation", "depreciationReserve",
            "salvageValue", "category", "categoryDescription", "locationSegment1", "locationSegment2",
            "locationSegment3", "locationSegment4", "locations", "sequenceNumber", "createdBy", "createdDate",
            "updatedBy", "updatedDate", "monthlyDepreciationAmt", "accumulatedDepreciationAmt", "depreciationDate",
            "netCost", "statusFlag", "changedBy", "insertedBy", "financialApproval", "changedDate", "nodeType"
    };

    // Whitelist allowed filter columns (add all relevant from entity)
    private static final List<String> ALLOWED_COLUMNS = Arrays.asList(COLUMNS);

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @Override
    public void streamExportToCsv(PrintWriter writer, String column, String value, String operator) throws IOException {
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(String.join(", ", COLUMNS));
        sql.append(" FROM tb_FarReport WHERE 1=1");

        List<Object> params = new ArrayList<>();

        if (column != null && value != null && ALLOWED_COLUMNS.contains(column)) {
            if ("contains".equalsIgnoreCase(operator)) {
                sql.append(" AND ").append(column).append(" LIKE ?");
                params.add("%" + value + "%");
            } else { // default to equals
                sql.append(" AND ").append(column).append(" = ?");
                params.add(value);
            }
        }

        // Write header
        writer.println(String.join(",", COLUMNS));

        // Stream rows
        jdbcTemplate.query(sql.toString(), params.toArray(), new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet rs) throws SQLException {
                StringBuilder row = new StringBuilder();
                for (String col : COLUMNS) {
                    Object val = rs.getObject(col);
                    String strVal;
                    if (val == null) {
                        strVal = "";
                    } else if (val instanceof Date) {
                        strVal = dateFormat.format((Date) val);
                    } else {
                        strVal = val.toString();
                    }
                    row.append(escapeCsv(strVal)).append(",");
                }
                // Remove last comma and write
                writer.println(row.substring(0, row.length() - 1));
            }
        });
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains("\"") || value.contains(",") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }



}
