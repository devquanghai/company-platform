package com.company.platform.tool.excel.model;

@FunctionalInterface public interface ExcelRowMapper<T> { T map(ExcelRowData row) throws Exception; }
