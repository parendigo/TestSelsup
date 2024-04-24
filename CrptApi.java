package org.example;

import com.google.common.util.concurrent.RateLimiter;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

class CrptApi {
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final RateLimiter rateLimiter;

    protected CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.rateLimiter = RateLimiter.create((double)requestLimit / timeUnit.toSeconds(1));
    }

    public void createDoc(JSONObject object, String name) {
        try {
            rateLimiter.acquire();
            String json = object.toString();
            CloseableHttpClient httpClient = HttpClientBuilder.create().build();
            HttpPost httpPost = new HttpPost("https://ismp.crpt.ru/api/v3/lk/documents/create");
            StringEntity entity = new StringEntity(json);
            httpPost.addHeader(name, "application/json");
            httpPost.setEntity(entity);
            HttpResponse response = httpClient.execute(httpPost);
        } catch (Exception eIgnored) {
        }
    }
}