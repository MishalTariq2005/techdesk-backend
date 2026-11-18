package com.example.file_service.file.exception;

/**
 * Thrown by FileAccessControlService whenever a caller isn't authorized to
 * access a file — whether because the parent lookup itself failed (fail
 * closed, see TicketLookupClient/GadgetRequestLookupClient) or because the
 * caller genuinely isn't the creator/assignee/manager the roadmap's rule
 * requires. Deliberately ONE exception type for both cases — the caller
 * doesn't need to know WHY internally (parent unreachable vs. genuinely not
 * authorized); either way the answer is 403.
 */
public class FileAccessDeniedException extends RuntimeException {
    public FileAccessDeniedException(String message) {
        super(message);
    }
}
