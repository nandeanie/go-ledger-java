package com.goledger.web.dto;

public record CurrencyTotal(String currency, long netAmount, long accountCount, boolean balanced) {}
