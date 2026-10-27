package com.chiefai.personalagent.expenses.application;

import java.util.List;

public record ExpenseReview(boolean required, List<String> reasons) {
}
