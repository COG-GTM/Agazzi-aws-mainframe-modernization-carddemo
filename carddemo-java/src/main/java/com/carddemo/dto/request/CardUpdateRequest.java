package com.carddemo.dto.request;

public record CardUpdateRequest(
        String embossedName,
        String activeStatus
) {}
