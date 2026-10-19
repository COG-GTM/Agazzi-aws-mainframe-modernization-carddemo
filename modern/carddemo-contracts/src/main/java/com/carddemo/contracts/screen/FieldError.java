package com.carddemo.contracts.screen;

/** Validation error on a BMS field ({@code field} = BMS field name, e.g. {@code CARDNIN}). */
public record FieldError(String field, String message) {}
