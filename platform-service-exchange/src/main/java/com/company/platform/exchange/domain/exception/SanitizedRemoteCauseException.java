package com.company.platform.exchange.domain.exception;

/** Safe cause marker that retains only the remote/vendor failure type. */
public final class SanitizedRemoteCauseException extends RuntimeException {
    public SanitizedRemoteCauseException(Throwable failure) {
        super(failure == null ? "unknown remote failure"
            : "remote failure type=" + failure.getClass().getName(), null, false, false);
    }
}
