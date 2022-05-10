package com.axelor.apps.warehouse.service;

import com.axelor.apps.warehouse.db.WarehouseInvoiceLine;
import com.axelor.exception.AxelorException;

public interface WarehouseInvoiceLineService {
  void setProductQty(WarehouseInvoiceLine invoiceLine) throws AxelorException;
}
