package com.axelor.apps.warehouse.service;

import com.axelor.apps.warehouse.db.WarehouseInvoiceLine;
import com.axelor.apps.warehouse.db.WarehouseProduct;
import com.axelor.apps.warehouse.db.repo.WarehouseProductRepository;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;

public class WarehouseInvoiceLineServiceImpl implements WarehouseInvoiceLineService {

  @Override
  @Transactional
  public void setProductQty(WarehouseInvoiceLine invoiceLine) throws AxelorException {

    WarehouseProductRepository prodRepo = Beans.get(WarehouseProductRepository.class);

    WarehouseProduct product = invoiceLine.getProduct();
    if (product == null) {
      throw new AxelorException(0, "Please select the product on form.");
    }

    BigDecimal invoiceSingleQty = invoiceLine.getSingleQty();
    BigDecimal invoiceUnitQty = invoiceLine.getUnitQty();

    BigDecimal productUnitQty = product.getUnitstock();
    BigDecimal productSingleQty = product.getExtraSinglePiece();

    BigDecimal avlProductUnitQty = productUnitQty.subtract(invoiceUnitQty);
    BigDecimal avlProductSingleQty = productSingleQty.subtract(invoiceSingleQty);

    product.setUnitstock(avlProductUnitQty);
    product.setExtraSinglePiece(avlProductSingleQty);

    BigDecimal unitPiece = avlProductUnitQty.multiply(product.getUnitPiece());
    BigDecimal totalPieceQty = unitPiece.add(product.getExtraSinglePiece());
    product.setTotalPiece(totalPieceQty);
    prodRepo.save(product);
  }
}
