package com.axelor.apps.warehouse.web;

import com.axelor.app.AppSettings;
import com.axelor.apps.warehouse.db.SignNowConfig;
import com.axelor.apps.warehouse.db.WarehouseInvoice;
import com.axelor.apps.warehouse.db.WarehouseInvoiceLine;
import com.axelor.apps.warehouse.db.WarehouseProduct;
import com.axelor.apps.warehouse.db.repo.SignNowConfigRepository;
import com.axelor.apps.warehouse.db.repo.WarehouseInvoiceRepository;
import com.axelor.apps.warehouse.db.repo.WarehouseProductRepository;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

@Singleton
public class SignNowDocumentController {

  public void uploadSignNowDocFromSaleOrder(ActionRequest request, ActionResponse response)
      throws ClientProtocolException, IOException {

    WarehouseInvoice invoice = request.getContext().asType(WarehouseInvoice.class);
    if (invoice.getEmailAddressStr() == null) {
      response.setError("Please fill out the email address to send an invitation for signature.");
      return;
    }
    MetaFile mataFile = invoice.getQuoteWithoutSign();
    if (mataFile == null) {
      response.setError("Please upload quotation pdf.");
      return;
    }

    String fileName = mataFile.getFileName();
    if (!fileName.substring(fileName.length() - 4, fileName.length()).equals(".pdf")) {
      response.setError("Please upload quotation only in pdf format.");
      return;
    }

    SignNowConfig signConfig =
        Beans.get(SignNowConfigRepository.class)
            .all()
            .filter("self.isTokenValidate = 'true'")
            .fetchOne();

    if (signConfig == null) {
      response.setError("Please configure signnow token.");
      return;
    }
    String token = signConfig.getToken();

    CloseableHttpClient httpClient = HttpClientBuilder.create().build();

    HttpPost postRequest = new HttpPost("https://api-eval.signnow.com/document");
    postRequest.setHeader("Authorization", token);
    //    postRequest.setHeader("Content-Type", "multipart/form-data");

    String pdfFileName = mataFile.getFileName();
    String attachmentPath = AppSettings.get().getPath("file.upload.dir", "");
    String fullPath = attachmentPath + "/" + pdfFileName;

    File file = new File(fullPath); // "/home/vikas/Java.pdf"
    MultipartEntityBuilder builder = MultipartEntityBuilder.create();

    // This attaches the file to the POST:
    builder.addBinaryBody(
        "file", new FileInputStream(file), ContentType.MULTIPART_FORM_DATA, file.getName());

    HttpEntity multipart = builder.build();
    postRequest.setEntity(multipart);

    HttpResponse responseDoc = httpClient.execute(postRequest);

    if (responseDoc != null) {
      String payload =
          EntityUtils.toString(
              responseDoc.getEntity()); // {"id":"9734f1193d0a4af68d7f8dc0b4865ff169e9a35e"}

      String resultID = "";
      try {
        JSONObject jsonObj = new JSONObject(payload);
        resultID = (String) jsonObj.get("id");
      } catch (Exception e) {
        response.setError("Error from signnow document upload: " + payload);
        return;
      }
      response.setValue("docId", resultID);
    }
  }

  public void sendSignNowInvitationFromSaleOrder(ActionRequest request, ActionResponse response)
      throws IOException {

    WarehouseInvoice invoice = request.getContext().asType(WarehouseInvoice.class);

    if (invoice.getDocId() == null || invoice.getDocId().equals("")) {
      response.setValue("isSendInvitation", false);
      response.setValue("statusSelect", 1);
      response.setNotify("The sale quotation is not upload on signnow account");
      return;
    }

    String docId = invoice.getDocId();

    SignNowConfig signConfig =
        Beans.get(SignNowConfigRepository.class)
            .all()
            .filter("self.isTokenValidate = 'true'")
            .fetchOne();

    if (signConfig == null) {
      response.setValue("isSendInvitation", false);
      response.setValue("statusSelect", 1);
      response.setNotify("Please configure signnow token.");
      return;
    }
    String token = signConfig.getToken();
    String fromEmail = signConfig.getEmailAddress();

    String customerEmail = invoice.getEmailAddressStr();
    if (customerEmail == null || customerEmail.equals("")) {
      response.setValue("isSendInvitation", false);
      response.setValue("statusSelect", 1);
      response.setNotify("Please write customer emial.");
      return;
    }

    CloseableHttpClient httpClient = HttpClients.createDefault();
    HttpPost httpRequest =
        new HttpPost("https://api-eval.signnow.com/document/" + docId + "/invite");

    httpRequest.addHeader("Authorization", token);

    StringEntity params =
        new StringEntity(
            "{\"document_id\":\""
                + docId
                + "\",\"subject\":\"Sale Quotation\",\"message\":\"ARPA has invited you to review and sign Sale Order: '"
                + invoice.getInvoiceNumber()
                + "'. Please click on 'View Document' to see the sale order. \\n \\n If you have questions or believe the order needs to be changed, please contact your sales representative to make the necessary changes and reissue the sale order. Thank you! \",\"from\":\""
                + fromEmail
                + "\",\"to\":\""
                + customerEmail
                + "\"} ");
    httpRequest.addHeader("content-type", "application/x-www-form-urlencoded");
    httpRequest.setEntity(params);

    try (CloseableHttpResponse httpResponse = httpClient.execute(httpRequest)) {

      HttpEntity entity = httpResponse.getEntity();
      if (entity != null) {
        String payload = EntityUtils.toString(entity);
        // {"result":"success","id":"b333e0babe014c0eaadaf35f251e09d00f513e7f","callback_url":"none"}

        String result = "";
        String invitationId = "";
        try {
          JSONObject jsonObj = new JSONObject(payload);
          result = (String) jsonObj.get("result");
          invitationId = (String) jsonObj.get("id");
        } catch (Exception e) {
          response.setValue("statusSelect", 1);
          response.setValue("isSendInvitation", false);
          response.setNotify("Error from signnow the invitation not sent: " + payload);
          return;
        }

        if (result != "" && result.equals("success")) {
          response.setValue("isSendInvitation", true);
          response.setValue("invitationId", invitationId);
          response.setValue("statusSelect", 2);
        } else {
          response.setValue("isSendInvitation", false);
          response.setValue("statusSelect", 1);
          response.setNotify("Error from signnow the invitation not sent: " + payload);
        }
      }

    } catch (Exception e) {
      response.setValue("statusSelect", 1);
      response.setValue("isSendInvitation", false);
      response.setNotify("Error from SignNow : " + e.toString());
    }
  }

  @Transactional
  public void getSignQuoteFromSaleOrder(ActionRequest request, ActionResponse response)
      throws IOException, AxelorException {
    WarehouseInvoice invoice = request.getContext().asType(WarehouseInvoice.class);

    CloseableHttpClient httpClient = HttpClients.createDefault();

    if (invoice.getQuotationPdf() != null) {
      response.setError(
          "Your quote is already signed. Please view the signed order pdf to review it.");
      return;
    }

    WarehouseInvoice order = Beans.get(WarehouseInvoiceRepository.class).find(invoice.getId());
    if (!order.getIsSendInvitation()) {
      response.setError("You have not sent the invitation for sign the quotation.");
      return;
    }

    if (order.getQuoteWithoutSign() == null) {
      response.setError("You have not upload the quotation.");
      return;
    }

    String docId = order.getDocId();

    if (docId == null || docId == "") {
      response.setError("Quotation not upload please upload quote again.");
      return;
    }

    SignNowConfig signConfig =
        Beans.get(SignNowConfigRepository.class)
            .all()
            .filter("self.isTokenValidate = 'true'")
            .fetchOne();

    if (signConfig == null) {
      response.setError("Please Configure SignNow Token.");
      return;
    }
    String token = signConfig.getToken();

    HttpGet httpRequest = new HttpGet("https://api-eval.signnow.com/document/" + docId + "");

    httpRequest.addHeader("Authorization", token);

    try (CloseableHttpResponse httpRresponse = httpClient.execute(httpRequest)) {
      HttpEntity entity = httpRresponse.getEntity();

      if (entity != null) {
        String result = EntityUtils.toString(entity);
        JSONObject jsonObj = new JSONObject(result);
        if (jsonObj.has("signatures")) {
          JSONArray signeturesArray = (JSONArray) jsonObj.get("signatures");
          if (signeturesArray.length() >= 1) {

            //////

            String fileName = invoice.getQuoteWithoutSign().getFileName();

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

              prodRepo.save(product);
            }

            order.setQuotationPdf(signFile);
            order.setStatusSelect(3);
            Beans.get(WarehouseInvoiceRepository.class).save(order);
            response.setReload(true);
          } else {
            response.setAlert("Quote still not signed by your customer.");
          }
        }
      }
    } catch (Exception e) {
      response.setNotify(e.toString());
      return;
    }
  }

  //  public void cancelInvitationFromSaleOrder(ActionRequest request, ActionResponse response)
  //      throws ClientProtocolException, IOException {
  //    SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);
  //    //	    SignNowDocument doc = saleOrder.getQuotationDoc();
  //
  //    if (saleOrder.getQuoteWithoutSign() == null) {
  //      response.setValue("isSendInvitation", false);
  //      response.setValue("statusSelect", 1);
  //    }
  //
  //    if (saleOrder.getInvitationId() == null || saleOrder.getInvitationId().equals("")) {
  //      return;
  //    }
  //    String invitationId = saleOrder.getInvitationId();
  //
  //    if (saleOrder.getQuoteWithoutSign() != null && saleOrder.getDocId() != null) {
  //      String docId = saleOrder.getDocId();
  //
  //      SignNowConfig signConfig =
  //          Beans.get(SignNowConfigRepository.class)
  //              .all()
  //              .filter("self.isTokenValidate = 'true'")
  //              .fetchOne();
  //
  //      if (signConfig == null) {
  //        response.setError("Please Configure SignNow Token.");
  //        return;
  //      }
  //
  //      String token = signConfig.getToken();
  //
  //      CloseableHttpClient httpClient = HttpClientBuilder.create().build();
  //
  //      HttpPut putRequest =
  //          new HttpPut("https://app-eval.signnow.com/api/invite/" + invitationId + "/cancel");
  //
  //      putRequest.setHeader("Authorization", token);
  //      HttpResponse responseData = httpClient.execute(putRequest);
  //
  //      String statusDoc = "";
  //      if (responseData != null) {
  //        String payload =
  //            EntityUtils.toString(
  //                responseData.getEntity()); // {"id":"9734f1193d0a4af68d7f8dc0b4865ff169e9a35e"}
  //
  //        try {
  //          JSONObject jsonObj = new JSONObject(payload);
  //          statusDoc = (String) jsonObj.get("id");
  //        } catch (Exception e) {
  //          response.setError("Error from SignNow Document Invitation remove: " + payload);
  //          return;
  //        }
  //      }
  //    }
  //  }
  //
  //  public void changeQuoteFromSaleOrder(ActionRequest request, ActionResponse response)
  //      throws ClientProtocolException, IOException {
  //    SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);
  //    //	    SignNowDocument doc = saleOrder.getQuotationDoc();
  //
  //    if (saleOrder.getQuoteWithoutSign() == null) {
  //      response.setValue("isSendInvitation", false);
  //      response.setValue("statusSelect", 1);
  //    }
  //
  //    if (saleOrder.getQuoteWithoutSign() != null && saleOrder.getDocId() != null) {
  //      String docId = saleOrder.getDocId();
  //
  //      SignNowConfig signConfig =
  //          Beans.get(SignNowConfigRepository.class)
  //              .all()
  //              .filter("self.isTokenValidate = 'true'")
  //              .fetchOne();
  //
  //      if (signConfig == null) {
  //        response.setError("Please Configure SignNow Token.");
  //        return;
  //      }
  //
  //      String token = signConfig.getToken();
  //
  //      CloseableHttpClient httpClient = HttpClientBuilder.create().build();
  //
  //      HttpDelete postRequest =
  //          new HttpDelete("https://api-eval.signnow.com/document/" + docId + "");
  //      postRequest.setHeader("Authorization", token);
  //      HttpResponse responseData = httpClient.execute(postRequest);
  //
  //      String statusDoc = "";
  //      if (responseData != null) {
  //        String payload =
  //            EntityUtils.toString(
  //                responseData.getEntity()); // {"id":"9734f1193d0a4af68d7f8dc0b4865ff169e9a35e"}
  //
  //        try {
  //          JSONObject jsonObj = new JSONObject(payload);
  //          statusDoc = (String) jsonObj.get("status");
  //        } catch (Exception e) {
  //          response.setError("Error from SignNow Document Delete: " + payload);
  //          return;
  //        }
  //      }
  //
  //      if (statusDoc.equals("success")) {
  //        response.setValue("quotationDoc", null);
  //        response.setValue("isSendInvitation", false);
  //        response.setValue("statusSelect", 1);
  //        response.setValue("docId", null);
  //        response.setValue("quoteWithoutSign", null);
  //        response.setValue("emailAddressStr", null);
  //        response.setValue("invitationId", null);
  //      }
  //    }
  //  }
  //
  //  public void validateSignOrderFromSaleOrder(ActionRequest request, ActionResponse response)
  //      throws IOException {
  //    Context context = request.getContext();
  //    List<Long> ids = new ArrayList<Long>();
  //    try {
  //      ids =
  //          (List)
  //              (((List) context.get("_ids"))
  //                  .stream()
  //                      .filter(ObjectUtils::notEmpty)
  //                      .map(input -> Long.parseLong(input.toString()))
  //                      .collect(Collectors.toList()));
  //
  //    } catch (Exception e) {
  //      response.setError("Please select sale quote for check order confirmation.");
  //    }
  //    CloseableHttpClient httpClient = HttpClients.createDefault();
  //
  //    for (Long id : ids) {
  //      SaleOrder order = Beans.get(SaleOrderRepository.class).find(id);
  //      if (!order.getIsSendInvitation()) {
  //        continue;
  //      }
  //
  //      if (order.getQuoteWithoutSign() == null) {
  //        continue;
  //      }
  //
  //      String docId = order.getDocId();
  //
  //      if (docId == null || docId.equals("")) {
  //        continue;
  //      }
  //
  //      SignNowConfig signConfig =
  //          Beans.get(SignNowConfigRepository.class)
  //              .all()
  //              .filter("self.isTokenValidate = 'true'")
  //              .fetchOne();
  //
  //      if (signConfig == null) {
  //        response.setError("Please Configure SignNow Token.");
  //        return;
  //      }
  //      String token = signConfig.getToken();
  //
  //      HttpGet httpRequest = new HttpGet("https://api-eval.signnow.com/document/" + docId + "");
  //
  //      httpRequest.addHeader("Authorization", token);
  //
  //      try (CloseableHttpResponse httpRresponse = httpClient.execute(httpRequest)) {
  //        HttpEntity entity = httpRresponse.getEntity();
  //
  //        if (entity != null) {
  //          String result = EntityUtils.toString(entity);
  //          JSONObject jsonObj = new JSONObject(result);
  //          if (jsonObj.has("signatures")) {
  //            JSONArray signeturesArray = (JSONArray) jsonObj.get("signatures");
  //            if (signeturesArray.length() >= 1) {
  //              Beans.get(SaleOrderWorkflowService.class).confirmSaleOrder(order);
  //            }
  //          }
  //        }
  //      } catch (Exception e) {
  //        response.setNotify(e.toString());
  //        continue;
  //      }
  //    }
  //  }
  //
  //  public void removeQuoteFromSaleOrder(ActionRequest request, ActionResponse response)
  //      throws ClientProtocolException, IOException {
  //    SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);
  //    //	    SignNowDocument doc = saleOrder.getQuotationDoc();
  //
  //    if (saleOrder.getQuoteWithoutSign() == null) {
  //      response.setValue("isSendInvitation", false);
  //    }
  //
  //    if (saleOrder.getQuoteWithoutSign() != null && saleOrder.getDocId() != null) {
  //      String docId = saleOrder.getDocId();
  //
  //      SignNowConfig signConfig =
  //          Beans.get(SignNowConfigRepository.class)
  //              .all()
  //              .filter("self.isTokenValidate = 'true'")
  //              .fetchOne();
  //
  //      if (signConfig == null) {
  //        response.setError("Please Configure SignNow Token.");
  //        return;
  //      }
  //
  //      String token = signConfig.getToken();
  //
  //      CloseableHttpClient httpClient = HttpClientBuilder.create().build();
  //      HttpDelete postRequest =
  //          new HttpDelete("https://api-eval.signnow.com/document/" + docId + "");
  //      postRequest.setHeader("Authorization", token);
  //      HttpResponse responseData = httpClient.execute(postRequest);
  //
  //      String statusDoc = "";
  //      if (responseData != null) {
  //        String payload =
  //            EntityUtils.toString(
  //                responseData.getEntity()); // {"id":"9734f1193d0a4af68d7f8dc0b4865ff169e9a35e"}
  //
  //        try {
  //          JSONObject jsonObj = new JSONObject(payload);
  //          statusDoc = (String) jsonObj.get("status");
  //        } catch (Exception e) {
  //          response.setError("Error from SignNow Document Delete: " + payload);
  //          return;
  //        }
  //      }
  //
  //      if (statusDoc.equals("success")) {
  //        return;
  //      }
  //    }
  //  }
}
