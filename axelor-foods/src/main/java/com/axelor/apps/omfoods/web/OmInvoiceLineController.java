package com.axelor.apps.omfoods.web;

import com.axelor.apps.om.db.OmInvoiceLine;
import com.axelor.apps.om.db.OmProduct;
import com.axelor.apps.omfoods.service.OmInvoiceLineService;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import com.ibm.icu.math.BigDecimal;

@Singleton
public class OmInvoiceLineController {

  public void setProductQty(ActionRequest request, ActionResponse response) throws AxelorException {
    OmInvoiceLine invoiceLine = request.getContext().asType(OmInvoiceLine.class);

    OmProduct product = invoiceLine.getProduct();
    if (product == null) {
      response.setError("Please select Product");
      return;
    }

    BigDecimal singleQty = new BigDecimal(invoiceLine.getSingleQty());
    BigDecimal unitQty = new BigDecimal(invoiceLine.getUnitQty());
    System.err.println();
    BigDecimal prodSingQty = new BigDecimal(product.getExtraSinglePiece());
    BigDecimal prodUnitQty = new BigDecimal(product.getUnitstock());

    if (unitQty.compareTo(prodUnitQty) == 1) {
      response.setValue("unitQty", new BigDecimal(0));
      response.setValue(
          "status",
          "This product has " + prodUnitQty + " Unit and " + prodSingQty + " Single Quntity.");
    }

    if (singleQty.compareTo(prodSingQty) == 1) {
      response.setValue("singleQty", new BigDecimal(0));
      response.setValue(
          "status",
          "This product has " + prodUnitQty + " Unit and " + prodSingQty + " Single Quntity.");
      return;
    }
  }

  public void getAvailableQTY(ActionRequest request, ActionResponse response)
      throws AxelorException {
    OmInvoiceLine invoiceLine = request.getContext().asType(OmInvoiceLine.class);
    OmProduct product = invoiceLine.getProduct();
    if (product == null) {
      response.setError("Please select Product");
      return;
    }
    BigDecimal prodSingQty = new BigDecimal(product.getExtraSinglePiece());
    BigDecimal prodUnitQty = new BigDecimal(product.getUnitstock());
    response.setValue(
        "status",
        "This product has " + prodUnitQty + " Unit and " + prodSingQty + " Single Quntity.");
  }

  public void lessProductQty(ActionRequest request, ActionResponse response)
      throws AxelorException {
    OmInvoiceLine invoiceLine = request.getContext().asType(OmInvoiceLine.class);

    OmInvoiceLineService iLine = Beans.get(OmInvoiceLineService.class);
    iLine.setProductQty(invoiceLine);
    //	    response.setCanClose(true);
  }

  public void checkConfirm(ActionRequest request, ActionResponse response) throws AxelorException {
    OmInvoiceLine invoiceLine = request.getContext().asType(OmInvoiceLine.class);
    if (invoiceLine.getConfirm() == false) {

      throw new AxelorException(0, "Please Confirm The product");
    }
  }
}
