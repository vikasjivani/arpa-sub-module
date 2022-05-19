package com.axelor.apps.warehouse.web;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.warehouse.db.WarehouseInvoice;
import com.axelor.apps.warehouse.db.WarehouseInvoiceLine;
import com.axelor.apps.warehouse.db.WarehouseProduct;
import com.axelor.apps.warehouse.db.repo.WarehouseInvoiceLineRepository;
import com.axelor.apps.warehouse.db.repo.WarehouseProductRepository;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class WarehouseInvoiceController {
  public void calculateTotal(ActionRequest request, ActionResponse response)
      throws AxelorException {
    WarehouseInvoice invoice = request.getContext().asType(WarehouseInvoice.class);
    BigDecimal totalCost = new BigDecimal(0);
    BigDecimal totalTax = new BigDecimal(0);

    if (invoice.getProducts() == null) {
      response.setValue("invoiceAmount", totalCost);
      return;
    }

    List<WarehouseInvoiceLine> invoiceLines = invoice.getProducts();
    for (WarehouseInvoiceLine invoiceLine : invoiceLines) {
      totalCost = totalCost.add(invoiceLine.getTotalCost());
      totalTax = totalTax.add(invoiceLine.getTaxAmount());
    }
    //    BigDecimal taxAmount = totalCost.multiply(new BigDecimal(0.07));
    BigDecimal grossAmount = totalTax.add(totalCost);

    response.setValue("invoiceAmount", totalCost);
    response.setValue("invoiceTax", totalTax);
    response.setValue("grossAmount", grossAmount);
  }

  @Transactional
  public void removeProduct(ActionRequest request, ActionResponse response) throws AxelorException {
    WarehouseInvoice invoice = request.getContext().asType(WarehouseInvoice.class);
    WarehouseInvoiceLineRepository invoiceLineRepo =
        Beans.get(WarehouseInvoiceLineRepository.class);
    WarehouseProductRepository prodRepo = Beans.get(WarehouseProductRepository.class);

    List<WarehouseInvoiceLine> invoiceLines = new ArrayList<WarehouseInvoiceLine>();
    List<WarehouseInvoiceLine> updatedInvoiceLines = new ArrayList<WarehouseInvoiceLine>();
    invoiceLines = invoice.getProducts();

    for (WarehouseInvoiceLine invoiceLine : invoiceLines) {
      if (invoiceLine.isSelected()) {
        WarehouseProduct product = invoiceLine.getProduct();

        BigDecimal invoiceProdPiece = invoiceLine.getTotalPiece();
        BigDecimal invoiceProdCartoon = invoiceLine.getTotalCartoon();

        BigDecimal productPieceQty = product.getTotalPiece();
        BigDecimal productCartoonQty = product.getUnitstock();

        BigDecimal avlProductPieceQty = productPieceQty.add(invoiceProdPiece);
        BigDecimal avlProductCartoonQty = productCartoonQty.add(invoiceProdCartoon);

        product.setTotalPiece(avlProductPieceQty);
        product.setUnitstock(avlProductCartoonQty);

        //        BigDecimal invoiceSingleQty = invoiceLine.getSingleQty();
        //        BigDecimal invoiceUnitQty = invoiceLine.getUnitQty();
        //
        //        BigDecimal productUnitQty = product.getUnitstock();
        //        BigDecimal productSingleQty = product.getExtraSinglePiece();
        //
        //        BigDecimal avlProductUnitQty = productUnitQty.add(invoiceUnitQty);
        //        BigDecimal avlProductSingleQty = productSingleQty.add(invoiceSingleQty);
        //
        //        product.setUnitstock(avlProductUnitQty);
        //        product.setExtraSinglePiece(avlProductSingleQty);

        //        BigDecimal unitPiece = avlProductUnitQty.multiply(product.getUnitPiece());
        //        BigDecimal totalPieceQty = unitPiece.add(product.getExtraSinglePiece());
        //        product.setTotalPiece(totalPieceQty);

        prodRepo.save(product);
      } else {
        updatedInvoiceLines.add(invoiceLine);
      }
    }

    response.setValue("products", updatedInvoiceLines);
  }

  @Transactional
  public void lessProductQty(ActionRequest request, ActionResponse response)
      throws AxelorException {
    WarehouseInvoice invoice = request.getContext().asType(WarehouseInvoice.class);

    WarehouseProductRepository prodRepo = Beans.get(WarehouseProductRepository.class);

    List<WarehouseInvoiceLine> invoiceLines = new ArrayList<WarehouseInvoiceLine>();
    invoiceLines = invoice.getProducts();

    for (WarehouseInvoiceLine invoiceLine : invoiceLines) {
      WarehouseProduct product = invoiceLine.getProduct();

      BigDecimal invoiceProdPiece = invoiceLine.getTotalPiece();
      BigDecimal invoiceProdCartoon = invoiceLine.getTotalCartoon();

      BigDecimal productPieceQty = product.getTotalPiece();
      BigDecimal productCartoonQty = product.getUnitstock();

      BigDecimal avlProductPieceQty = productPieceQty.subtract(invoiceProdPiece);
      BigDecimal avlProductCartoonQty = productCartoonQty.subtract(invoiceProdCartoon);

      product.setTotalPiece(avlProductPieceQty);
      product.setUnitstock(avlProductCartoonQty);

      //
      //      BigDecimal invoiceSingleQty = invoiceLine.getSingleQty();
      //      BigDecimal invoiceUnitQty = invoiceLine.getUnitQty();
      //
      //      BigDecimal productUnitQty = product.getUnitstock();
      //      BigDecimal productSingleQty = product.getExtraSinglePiece();
      //
      //      BigDecimal avlProductUnitQty = productUnitQty.subtract(invoiceUnitQty);
      //      BigDecimal avlProductSingleQty = productSingleQty.subtract(invoiceSingleQty);
      //
      //      product.setUnitstock(avlProductCartoonQty);
      //      product.setExtraSinglePiece(avlProductSingleQty);

      //      BigDecimal unitPiece = avlProductUnitQty.multiply(product.getUnitPiece());
      //      BigDecimal totalPieceQty = unitPiece.add(product.getExtraSinglePiece());
      //      product.setTotalPiece(totalPieceQty);

      prodRepo.save(product);
    }
  }

  @Transactional
  public void resetProduct(ActionRequest request, ActionResponse response) throws AxelorException {

    WarehouseInvoice invoice = request.getContext().asType(WarehouseInvoice.class);
    WarehouseProductRepository prodRepo = Beans.get(WarehouseProductRepository.class);

    List<WarehouseInvoiceLine> invoiceLines = new ArrayList<WarehouseInvoiceLine>();
    invoiceLines = invoice.getProducts();

    for (WarehouseInvoiceLine invoiceLine : invoiceLines) {
      WarehouseProduct product = invoiceLine.getProduct();

      BigDecimal invoiceProdPiece = invoiceLine.getTotalPiece();
      BigDecimal invoiceProdCartoon = invoiceLine.getTotalCartoon();

      BigDecimal productPieceQty = product.getTotalPiece();
      BigDecimal productCartoonQty = product.getUnitstock();

      BigDecimal avlProductPieceQty = productPieceQty.add(invoiceProdPiece);
      BigDecimal avlProductCartoonQty = productCartoonQty.add(invoiceProdCartoon);

      product.setTotalPiece(avlProductPieceQty);
      product.setUnitstock(avlProductCartoonQty);

      //      BigDecimal invoiceSingleQty = invoiceLine.getSingleQty();
      //      BigDecimal invoiceUnitQty = invoiceLine.getUnitQty();
      //
      //      BigDecimal productUnitQty = product.getUnitstock();
      //      BigDecimal productSingleQty = product.getExtraSinglePiece();
      //
      //      BigDecimal avlProductUnitQty = productUnitQty.add(invoiceUnitQty);
      //      BigDecimal avlProductSingleQty = productSingleQty.add(invoiceSingleQty);
      //
      //      product.setUnitstock(avlProductUnitQty);
      //      product.setExtraSinglePiece(avlProductSingleQty);
      //
      //      BigDecimal unitPiece = avlProductUnitQty.multiply(product.getUnitPiece());
      //      BigDecimal totalPieceQty = unitPiece.add(product.getExtraSinglePiece());
      //      product.setTotalPiece(totalPieceQty);

      prodRepo.save(product);
    }

    response.setValue("products", null);
  }

  public void printReport(ActionRequest request, ActionResponse response) throws AxelorException {
    WarehouseInvoice invoice = request.getContext().asType(WarehouseInvoice.class);
    String fileLink =
        ReportFactory.createReport("test.rptdesign", "WarehouseInvoice" + "-${date}")
            .addParam("WarehouseInvoiceId", invoice.getId())
            .generate()
            .getFileLink();

    response.setView(ActionView.define("WarehouseInvoice").add("html", fileLink).map());
  }
}
