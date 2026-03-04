package com.zain.ksa.alm.financials.mapper;

import com.zain.ksa.alm.financials.dto.response.*;
import com.zain.ksa.alm.financials.entity.*;
import org.mapstruct.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * MapStruct mapper.
 */
@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface InventoryMapper {

    // ─── DepreciationHistory ──────────────────────────────────────────────────

    DepreciationHistoryDTO toDto(DepreciationHistory entity);

    // ─── FarReport ────────────────────────────────────────────────────────────

    /**
     * Maps FarReport entity to FarReportDTO.
     * Used by the streaming export pipeline for FAR report exports.
     *
     * <p>All field names match 1:1 between entity and DTO, so MapStruct
     * generates the implementation automatically — no @Mapping annotations needed.</p>
     */
    FarReportDTO toFarReportDto(FarReport entity);

    // ─── Node (Active Inventory) ──────────────────────────────────────────────

    NodeDTO toDto(Node entity);

    // ─── PassiveInventory ─────────────────────────────────────────────────────

    PassiveInventoryDTO toDto(PassiveInventory entity);

    // ─── IT Inventory ─────────────────────────────────────────────────────────

    ITInventoryDTO toDto(ITInventory entity);

    // ─── Unmapped Active ──────────────────────────────────────────────────────

    UnmappedActiveInventoryDTO toDto(UnmappedActiveInventory entity);

    /**
     * Node → UnmappedActiveInventory
     *
     * manufacturingDate: Node has java.util.Date (Hibernate returns java.sql.Date
     * at runtime). UnmappedActiveInventory expects LocalDate.
     * Use the safe @Named converter to avoid UnsupportedOperationException.
     */
    @Mapping(target = "recordNo",          ignore = true)
    @Mapping(target = "recordDateTime",    expression = "java(java.time.LocalDateTime.now())")
    @Mapping(source = "nodeName",          target = "nodeName")
    @Mapping(source = "siteId",            target = "siteId",    qualifiedByName = "intToString")
    @Mapping(source = "inventoryType",     target = "nodeType",  qualifiedByName = "intToString")
    @Mapping(source = "manufacturingDate", target = "manufacturingDate", qualifiedByName = "dateToLocalDate")
    @Mapping(target = "nodeId",            ignore = true)
    @Mapping(target = "installationDate",  ignore = true)
    @Mapping(target = "assetInsertionDate",ignore = true)
    @Mapping(target = "warranty",          ignore = true)
    @Mapping(target = "manufacturer",      ignore = true)
    UnmappedActiveInventory toUnmapped(Node node);

    // ─── Unmapped Passive ─────────────────────────────────────────────────────

    UnmappedPassiveInventoryDTO toDto(UnmappedPassiveInventory entity);

    @Mapping(target = "id",             ignore = true)
    @Mapping(target = "recordDateTime", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(source = "recordNo",       target = "inventoryId", qualifiedByName = "intToString")
    UnmappedPassiveInventory toUnmapped(PassiveInventory item);

    // ─── Unmapped IT ──────────────────────────────────────────────────────────

    UnmappedITInventoryDTO toDto(UnmappedITInventory entity);

    @Mapping(target = "id",               ignore = true)
    @Mapping(target = "recordDatetime",   expression = "java(java.time.LocalDateTime.now())")
    @Mapping(source = "hostSerialNumber", target = "hostSerialNumber")
    @Mapping(source = "osId",             target = "osId",     qualifiedByName = "stringToInt")
    @Mapping(source = "virtual",          target = "isVirtual",qualifiedByName = "decimalToBool")
    UnmappedITInventory toUnmappedIT(ITInventory itInventory);

    // ─── Named converters ─────────────────────────────────────────────────────

    /**
     * Safe java.util.Date → LocalDate conversion.
     *
     * java.sql.Date (Hibernate's runtime type for DATE columns) throws
     * UnsupportedOperationException from toInstant(). Fix: check the
     * runtime type and call toLocalDate() directly on java.sql.Date.
     */
    @Named("dateToLocalDate")
    static LocalDate dateToLocalDate(Date date) {
        if (date == null) return null;
        if (date instanceof java.sql.Date) {
            return ((java.sql.Date) date).toLocalDate();
        }
        return date.toInstant()
                   .atZone(ZoneId.systemDefault())
                   .toLocalDate();
    }

    @Named("intToString")
    static String intToString(Integer value) {
        return value == null ? null : value.toString();
    }

    @Named("stringToInt")
    static Integer stringToInt(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { return null; }
    }

    @Named("decimalToBool")
    static Boolean decimalToBool(java.math.BigDecimal value) {
        return value != null && value.compareTo(java.math.BigDecimal.ZERO) != 0;
    }
}