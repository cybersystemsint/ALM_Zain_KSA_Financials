package com.telkom.co.ke.almoptics.common;


public class BatchResult {

    private int processed;
    private int inserted;
    private int updated;
    private int deleted;
    private int failed; // future

    public void incProcessed() { processed++; }
    public void incInserted()  { inserted++; }
    public void incUpdated()   { updated++; }
    public void incDeleted()   { deleted++; }
    public void incFailed()    { failed++; }

    public void add(BatchResult other) {
        this.processed += other.processed;
        this.inserted  += other.inserted;
        this.updated   += other.updated;
        this.deleted   += other.deleted;
        this.failed    += other.failed;
    }

    // getters (important for logging / returning)

    public int getProcessed() { return processed; }
    public int getInserted()  { return inserted; }
    public int getUpdated()   { return updated; }
    public int getDeleted()   { return deleted; }
    public int getFailed()    { return failed; }
}