package com.axelor.apps.warehouse.web;

import com.axelor.apps.warehouse.db.Container;
import com.axelor.apps.warehouse.db.ContainerProductLine;
import com.axelor.apps.warehouse.db.WarehouseProduct;
import com.axelor.apps.warehouse.db.repo.WarehouseProductRepository;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class ContainerController {
  public void calculateTotalCost(ActionRequest request, ActionResponse response)
      throws AxelorException {
    Container container = request.getContext().asType(Container.class);
    BigDecimal totalCost = new BigDecimal(0);
    BigDecimal prodCount = new BigDecimal(0);

    if (container.getProducts() == null) {
      response.setValue("totalProduct", prodCount);
      response.setValue("totalCost", totalCost);
      return;
    }

    List<ContainerProductLine> prodLines = container.getProducts();
    for (ContainerProductLine productLine : prodLines) {
      totalCost = totalCost.add(productLine.getTotalCost());
      prodCount = prodCount.add(new BigDecimal(1));
    }

    response.setValue("totalProduct", prodCount);
    response.setValue("totalCost", totalCost);
  }

  @Transactional
  public void addProductQty(ActionRequest request, ActionResponse response) throws AxelorException {
    Container container = request.getContext().asType(Container.class);
    WarehouseProductRepository prodRepo = Beans.get(WarehouseProductRepository.class);

    List<ContainerProductLine> containerProducts = new ArrayList<ContainerProductLine>();
    containerProducts = container.getProducts();

    for (ContainerProductLine containerProduct : containerProducts) {
      WarehouseProduct product = containerProduct.getProduct();

      BigDecimal containerProductPiece = containerProduct.getQty();

      BigDecimal productPieceQty = product.getTotalPiece();
      BigDecimal productCartoonPiece = product.getUnitPiece();

      BigDecimal avlProductPieceQty = productPieceQty.add(containerProductPiece);
      BigDecimal avlProductCartoonQty = avlProductPieceQty.divide(productCartoonPiece);

      product.setTotalPiece(avlProductPieceQty);
      product.setUnitstock(avlProductCartoonQty);

      prodRepo.save(product);
    }
  }

  @Transactional
  public void removeSelectedProduct(ActionRequest request, ActionResponse response)
      throws AxelorException {
    Container container = request.getContext().asType(Container.class);
    WarehouseProductRepository prodRepo = Beans.get(WarehouseProductRepository.class);

    List<ContainerProductLine> conProductLines = new ArrayList<ContainerProductLine>();
    List<ContainerProductLine> updatedProductLines = new ArrayList<ContainerProductLine>();
    conProductLines = container.getProducts();

    for (ContainerProductLine conProductLine : conProductLines) {
      if (conProductLine.isSelected()) {
        WarehouseProduct product = conProductLine.getProduct();

        BigDecimal containerProdPiece = conProductLine.getQty();

        BigDecimal productPieceQty = product.getTotalPiece();
        BigDecimal productCartoonPiece = product.getUnitPiece();

        BigDecimal avlProductPieceQty = productPieceQty.subtract(containerProdPiece);
        BigDecimal avlProductCartoonQty = avlProductPieceQty.divide(productCartoonPiece);

        product.setTotalPiece(avlProductPieceQty);
        product.setUnitstock(avlProductCartoonQty);

        prodRepo.save(product);
      } else {
        updatedProductLines.add(conProductLine);
      }
    }

    response.setValue("products", updatedProductLines);
  }

  @Transactional
  public void resetProduct(ActionRequest request, ActionResponse response) throws AxelorException {

    Container container = request.getContext().asType(Container.class);
    WarehouseProductRepository prodRepo = Beans.get(WarehouseProductRepository.class);

    List<ContainerProductLine> conProductLines = new ArrayList<ContainerProductLine>();
    conProductLines = container.getProducts();

    for (ContainerProductLine conProductLine : conProductLines) {
      WarehouseProduct product = conProductLine.getProduct();

      BigDecimal containerProdPiece = conProductLine.getQty();

      BigDecimal productPieceQty = product.getTotalPiece();
      BigDecimal productCartoonPiece = product.getUnitPiece();

      BigDecimal avlProductPieceQty = productPieceQty.subtract(containerProdPiece);
      BigDecimal avlProductCartoonQty = avlProductPieceQty.divide(productCartoonPiece);

      product.setTotalPiece(avlProductPieceQty);
      product.setUnitstock(avlProductCartoonQty);

      prodRepo.save(product);
    }

    response.setValue("products", null);
  }
}
