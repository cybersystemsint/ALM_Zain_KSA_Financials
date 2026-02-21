package com.telkom.co.ke.almoptics.entities;
import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "tb_Inventory_Type")
public class InventoryType implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int recordNo;
    
    private String inventoryTypeName;
    
    private int status;

// Getters and Setters
    public int getRecordNo() {
        return recordNo;
    }

    public void setRecordNo(int recordNo) {
        this.recordNo = recordNo;
    }

    public String getInventoryTypeName() {
        return inventoryTypeName;
    }

    public void setInventoryTypeName(String inventoryTypeName) {
        this.inventoryTypeName = inventoryTypeName;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }
}
