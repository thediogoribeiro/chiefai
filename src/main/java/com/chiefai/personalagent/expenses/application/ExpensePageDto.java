package com.chiefai.personalagent.expenses.application;

import java.util.List;

public record ExpensePageDto(List<ExpenseDto> content, int page, int size, long totalElements, int totalPages,
                             String sort) {
}
