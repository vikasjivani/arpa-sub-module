package com.axelor.apps.warehouse.web;

import com.axelor.apps.warehouse.db.SignNowConfig;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Singleton;
import java.io.IOException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

@Singleton
public class SignNowConfigController {

  public void generateToken(ActionRequest request, ActionResponse response)
      throws ClientProtocolException, IOException {
    Context context = request.getContext();
    SignNowConfig config = context.asType(SignNowConfig.class);

    if (config.getAuthorizationStr() == null || config.getAuthorizationStr().equals("")) {
      response.setError("Please enter authorization string from signnow.");
      return;
    }

    if (config.getUsername() == null || config.getUsername().equals("")) {
      response.setError("Please enter username for generate token.");
      return;
    }

    if (config.getPassword() == null || config.getPassword().equals("")) {
      response.setError("Please enter password for generae the token.");
      return;
    }

    if (config.getGrantType() == null || config.getGrantType().equals("")) {
      response.setError("Please enter grant type field.");
      return;
    }

    if (config.getScop() == null || config.getScop().equals("")) {
      response.setError("Please enter * in scop field.");
      return;
    }

    CloseableHttpClient httpClient = HttpClientBuilder.create().build();

    HttpPost postRequest = new HttpPost("https://api-eval.signnow.com/oauth2/token");
    postRequest.setHeader("Authorization", config.getAuthorizationStr());
    //    postRequest.setHeader("Content-Type", "multipart/form-data");

    MultipartEntityBuilder builder = MultipartEntityBuilder.create();
    builder.addTextBody("username", config.getUsername()); // Email id
    builder.addTextBody("password", config.getPassword()); // password
    builder.addTextBody("grant_type", config.getGrantType()); // password
    builder.addTextBody("scop", config.getScop()); // *

    // This attaches the file to the POST:

    HttpEntity multipart = builder.build();
    postRequest.setEntity(multipart);

    HttpResponse responseDoc = httpClient.execute(postRequest);

    if (responseDoc != null) {
      String payload =
          EntityUtils.toString(
              responseDoc.getEntity()); // {"id":"9734f1193d0a4af68d7f8dc0b4865ff169e9a35e"}

      try {
        JSONObject jsonObj = new JSONObject(payload);
        if (jsonObj.has("error")) {
          response.setError(
              "Signnow Login : Error code : " + jsonObj.get("code") + " : " + jsonObj.get("error"));
          return;
        }
        if (jsonObj.has("access_token")) {
          response.setValue("token", "bearer " + jsonObj.get("access_token"));
          response.setValue("isTokenValidate", false);
          response.setNotify("Login completed...!!");
        }
      } catch (Exception e) {
        response.setError("Error from SignNow login: " + payload);
        return;
      }
    }
  }
}
