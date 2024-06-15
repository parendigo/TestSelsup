package study;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.io.StringWriter;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


public class SendJsonHelper {
    private final long timeAmountMillis; // total refresh time
    private ListCallNode head; // List contains previous method calls
    private ListCallNode tail;
    private final int requestLimit;
    Semaphore semaphore; // contains requestAmount

//    private final String DefaultUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final String DefaultUrl = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    public SendJsonHelper(int requestLimit, TimeUnit timeUnit) {
        this.timeAmountMillis = timeUnit.toMillis(1);
        this.requestLimit = requestLimit;
        this.semaphore = new Semaphore(requestLimit, true);
    }

    public SendJsonHelper(int requestLimit, long timeAmount, TimeUnit timeUnit) {
        this.timeAmountMillis = timeUnit.toMillis(timeAmount);
        this.requestLimit = requestLimit;
        this.semaphore = new Semaphore(requestLimit, true);
    }

    public <T> boolean sendJsonHttpPost(T json, String signature) {
        if ((semaphore.availablePermits() * 10) % requestLimit == 0)
            refreshCounterAndDeleteOldCalls();
        if (semaphore.tryAcquire()) {
            addNewCallNode();
            sendJson(json, signature);
            return true;
        }
        return false;
    }

    private synchronized void refreshCounterAndDeleteOldCalls() {
        long currentTime = System.currentTimeMillis();
        while (head != null && currentTime - head.callTime >= timeAmountMillis) {
            head = head.next;
            semaphore.release();
        }
    }

    private void addNewCallNode() {
        ListCallNode lcn = new ListCallNode(System.currentTimeMillis());
        if (head == null) {
            head = lcn;
            tail = lcn;
        } else {
            tail.next = lcn;
            tail = lcn;
        }
    }

    private static class ListCallNode {
        long callTime;
        ListCallNode next;

        public ListCallNode(long time) {
            this.callTime = time;
        }
    }

    private <T> String sendJson(T json, String signature) {

        try (CloseableHttpClient client = HttpClients.createDefault()) {

            HttpPost httpPost = new HttpPost(DefaultUrl);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + signature);
            httpPost.setEntity(new StringEntity(parseJsonData(json)));

            ResponseHandler<String> responseHandler = new MyResponseHandler();
            return client.execute(httpPost, responseHandler);
        } catch (IOException e) {
            // log(e)
        }
        return null;
    }

    private String parseJsonData(String jsonString) {
        return jsonString;
    }

    private String parseJsonData(JSONObject jsonObject) {
        return jsonObject.toString();
    }

    private String parseJsonData(Object object) {
        ObjectMapper om = new ObjectMapper();
        StringWriter sw = new StringWriter();
        try {
            om.writeValue(sw, object);
        } catch (IOException e) {
            // log(e)
        }
        return sw.toString();
    }

    public static class MyResponseHandler implements ResponseHandler<String> {

        @Override
        public String handleResponse(HttpResponse response) throws IOException {
            int status = response.getStatusLine().getStatusCode();
            if (status >= 200 && status < 300) {
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    return Integer.toString(status);
                } else {
                    return EntityUtils.toString(entity);
                }
            } else return Integer.toString(status);
        }
    }
}
