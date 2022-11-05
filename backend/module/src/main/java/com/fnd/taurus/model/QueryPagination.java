package com.fnd.taurus.model;

import java.util.List;

public record QueryPagination(String q, Integer index, Integer size, List<String> sort) {
}
