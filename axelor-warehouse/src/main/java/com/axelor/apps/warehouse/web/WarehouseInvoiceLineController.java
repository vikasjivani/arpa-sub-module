package com.axelor.apps.warehouse.web;

import com.axelor.apps.warehouse.db.WarehouseInvoiceLine;
import com.axelor.apps.warehouse.db.WarehouseProduct;
import com.axelor.apps.warehouse.service.WarehouseInvoiceLineService;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import com.ibm.icu.math.BigDecimal;

@Singleton
public class WarehouseInvoiceLineController {

  public void setProductQty(ActionRequest request, ActionResponse response) throws AxelorException {
    WarehouseInvoiceLine invoiceLine = request.getContext().asType(WarehouseInvoiceLine.class);

    WarehouseProduct product = invoiceLine.getProduct();
    if (product == null) {
      response.setError("Please select Product");
      return;
    }

    BigDecimal totalLinePiece = new BigDecimal(invoiceLine.getTotalPiece());

    BigDecimal totalProdPiece = new BigDecimal(product.getTotalPiece());
    BigDecimal prodUnitQty = new BigDecimal(product.getUnitstock());

    if (totalLinePiece.compareTo(totalProdPiece) == 1) {
      response.setValue("totalPiece", new BigDecimal(0));
      response.setValue(
          "status",
          "This product has " + prodUnitQty + " Cartoon and/or " + totalProdPiece + " Piece.");
    }
  }

  public void getAvailableQTY(ActionRequest request, ActionResponse response)
      throws AxelorException {
    WarehouseInvoiceLine invoiceLine = request.getContext().asType(WarehouseInvoiceLine.class);
    WarehouseProduct product = invoiceLine.getProduct();
    if (product == null) {
      response.setError("Please select Product");
      return;
    }
    BigDecimal prodPiece = new BigDecimal(product.getTotalPiece());
    BigDecimal prodUnitQty = new BigDecimal(product.getUnitstock());
    response.setValue(
        "status",
        "This product has " + prodUnitQty + " Cartoon and/or " + prodPiece + " Single Quntity.");
  }

  public void lessProductQty(ActionRequest request, ActionResponse response)
      throws AxelorException {
    WarehouseInvoiceLine invoiceLine = request.getContext().asType(WarehouseInvoiceLine.class);

    WarehouseInvoiceLineService iLine = Beans.get(WarehouseInvoiceLineService.class);
    iLine.setProductQty(invoiceLine);
    //	    response.setCanClose(true);
  }
}
