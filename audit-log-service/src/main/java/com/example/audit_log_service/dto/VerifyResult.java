package com.example.audit_log_service.dto;

import java.util.List;

public record VerifyResult(
        int entriesChecked,
        int tamperedCount,
        List<Long> tamperedEntryIds
) {
    public boolean clean() {
        return tamperedCount == 0;
    }
}
