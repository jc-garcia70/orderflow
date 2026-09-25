package com.orderflow.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Details of a specific field validation error.
 */
public record ValidationError(
        @JsonProperty("field")
        String field,

        @JsonProperty("message")
        String message
) {
}
