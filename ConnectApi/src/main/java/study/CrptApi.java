package study;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("SpellCheckingInspection")
public class CrptApi {
    private final long timeAmountMillis; // total refresh time
    private final int requestLimit;
    private final Semaphore semaphore; // contains requestLimit

    private ListCallNode head; // List contains previous method calls
    private ListCallNode tail; // helps to add to the end of the List

    private static final int refreshRate = 10; // used to define frequency with which the List will be refreshed

    private static final String DEFAULT_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    /*private final String DefaultUrl = "http://localhost:8080/test";*/

    public CrptApi(int requestLimit, TimeUnit timeUnit) {
        this.timeAmountMillis = timeUnit.toMillis(1);
        this.requestLimit = requestLimit;
        this.semaphore = new Semaphore(requestLimit, true);
    }

    public CrptApi(int requestLimit, long timeAmount, TimeUnit timeUnit) {
        this.timeAmountMillis = timeUnit.toMillis(timeAmount);
        this.requestLimit = requestLimit;
        this.semaphore = new Semaphore(requestLimit, true);
    }

    //  returns null, if post was not executed due to requestlimit.
    //  returns body if status >= 200 && status < 300, otherwise returns status code.
    //  Can make method also synchronized to keep the sequence of posts, but it will affect the execution speed
    public <T> String sendJsonHttpPost(T json, String signature) {
        if ((semaphore.availablePermits() * refreshRate) % requestLimit == 0)
            refreshCounterAndDeleteOldCalls();
        if (semaphore.tryAcquire()) {
            addNewCallNode();
            return sendJson(json, signature);
        }
        return null;
    }

    //  Deletes all old notes about method calls and releases semaphore
    private synchronized void refreshCounterAndDeleteOldCalls() {
        long currentTime = System.currentTimeMillis();
        int count = 0;
        while (head != null && currentTime - head.callTime >= timeAmountMillis) {
            head = head.next;
            count++;
        }
        semaphore.release(count);
    }

    private synchronized void addNewCallNode() {
        ListCallNode lcn = new ListCallNode(System.currentTimeMillis());
        if (head == null)
            head = lcn;
        else
            tail.next = lcn;
        tail = lcn;
    }

    //  Contains nodes of recent method calls. Recent calls are added to the end
    private static class ListCallNode {
        long callTime;
        ListCallNode next;

        public ListCallNode(long time) {
            this.callTime = time;
        }
    }

    private <T> String sendJson(T json, String signature) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {

            HttpPost httpPost = new HttpPost(DEFAULT_URL);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + signature);
            httpPost.setEntity(new StringEntity(JsonDataToString(json)));

            ResponseHandler<String> responseHandler = new MyResponseHandler();
            return client.execute(httpPost, responseHandler);
        } catch (IOException e) {
            // log(e)
        }
        return null;
    }

    private <T> String JsonDataToString(T object) {
        if (object instanceof String) {
            System.out.println("String");
            return (String) object;
        } else if (object instanceof JSONObject) {
            System.out.println("JSONObject");
            return object.toString();
        }
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
                    StringBuilder sb = new StringBuilder();
                    try (InputStream in = entity.getContent()) {
                        byte[] buffer = new byte[4096];
                        while (in.available() > 0) {
                            int count = in.read(buffer);
                            sb.append(new String(buffer, 0, count));
                        }
                    }
                    return sb.toString();
                }
            } else return Integer.toString(status);
        }
    }
}