package com.ailoganalyzer.utils;

import java.util.Map;

public class ErrorConstants {

    public static final Map<String, ErrorDetail> ERROR_MAP = Map.of(
            "timeout", new ErrorDetail(
                    "Timeout issue detected",
                    "Check DB connection, increase timeout, optimize network",
                    "HIGH"
            ),
            "nullpointer", new ErrorDetail(
                    "NullPointerException detected",
                    "Add null checks, initialize variables properly",
                    "MEDIUM"
            ),
            "exception", new ErrorDetail(
                    "General exception occurred",
                    "Check stack trace and logs for root cause",
                    "MEDIUM"
            )
    );
}