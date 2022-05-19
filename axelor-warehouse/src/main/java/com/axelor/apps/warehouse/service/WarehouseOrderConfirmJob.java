package com.axelor.apps.warehouse.service;

import com.axelor.app.AppSettings;
import com.axelor.apps.warehouse.db.SignNowConfig;
import com.axelor.apps.warehouse.db.WarehouseInvoice;
import com.axelor.apps.warehouse.db.WarehouseInvoiceLine;
import com.axelor.apps.warehouse.db.WarehouseProduct;
import com.axelor.apps.warehouse.db.repo.SignNowConfigRepository;
import com.axelor.apps.warehouse.db.repo.WarehouseInvoiceRepository;
import com.axelor.apps.warehouse.db.repo.WarehouseProductRepository;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;

public class WarehouseOrderConfirmJob implements Job {

  private boolean isRunning(JobExecutionContext context) {
    try {
      return context.getScheduler().getCurrentlyExecutingJobs().stream()
          .filter(j -> j.getTrigger().equals(context.getTrigger()))
          .filter(j -> !j.getFireInstanceId().equals(context.getFireInstanceId()))
          .findFirst()
          .isPresent();
    } catch (SchedulerException e) {
      return false;
    }
  }

  @Override
  @Transactional
  public void execute(JobExecutionContext context) throws JobExecutionException {
    System.err.println("hhh");

    CloseableHttpClient httpClient = HttpClients.createDefault();

    List<WarehouseInvoice> invoices =
        (List<WarehouseInvoice>) Beans.get(WarehouseInvoiceRepository.class).all().fetch();
    for (WarehouseInvoice inv : invoices) {

      if (!inv.getIsSendInvitation()) {
        continue;
      }

      if (inv.getQuoteWithoutSign() == null) {
        continue;
      }

      String docId = inv.getDocId();

      if (docId == null || docId.equals("")) {
        continue;
      }

      SignNowConfig signConfig =
          Beans.get(SignNowConfigRepository.class)
              .all()
              .filter("self.isTokenValidate = 'true'")
              .fetchOne();

      String token = signConfig.getToken();

      HttpGet httpRequest = new HttpGet("https://api-eval.signnow.com/document/" + docId + "");

      httpRequest.addHeader("Authorization", token);
      System.err.println("hello");
      try (CloseableHttpResponse httpRresponse = httpClient.execute(httpRequest)) {
        HttpEntity entity = httpRresponse.getEntity();

        if (entity != null) {
          String result = EntityUtils.toString(entity);
          JSONObject jsonObj = new JSONObject(result);
          if (jsonObj.has("signatures")) {
            JSONArray signeturesArray = (JSONArray) jsonObj.get("signatures");
            if (signeturesArray.length() >= 1) {

              //////

              String fileName = inv.getQuoteWithoutSign().getFileName();

              HttpGet postRequest =
                  new HttpGet(
                      "https://api-eval.signnow.com/document/"
                          + docId
                          + "/download?type=collapsed&with_history=1");
              postRequest.setHeader("Authorization", token);
              HttpResponse responseData = httpClient.execute(postRequest);

              MetaFile metaFile = new MetaFile();
              metaFile.setFileName(fileName);

              String attachmentPath = AppSettings.get().getPath("file.upload.dir", "");
              String fullPath = attachmentPath + "/" + fileName;

              File fileInfo = new File(fullPath);

              HttpEntity entityDoc = responseData.getEntity();
              if (entityDoc != null) {
                InputStream inputStream = entityDoc.getContent();
                OutputStream outputStream = new FileOutputStream(fileInfo);
                IOUtils.copy(inputStream, outputStream);
                outputStream.close();
              }

              MetaFile signFile = Beans.get(MetaFiles.class).upload(fileInfo, metaFile);

              ///////

              WarehouseProductRepository prodRepo = Beans.get(WarehouseProductRepository.class);

              List<WarehouseInvoiceLine> invoiceLines = new ArrayList<WarehouseInvoiceLine>();
              invoiceLines = inv.getProducts();

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

                prodRepo.save(product);
              }

              inv.setQuotationPdf(signFile);
              inv.setStatusSelect(3);
              Beans.get(WarehouseInvoiceRepository.class).save(inv);
            }
          }
        }
      } catch (Exception e) {
        continue;
      }
    }
  }
}
