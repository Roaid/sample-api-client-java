package net.btcmarkets.api.sample;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

public class PlaceOrder {
    private String apiKey;
    private String privateKey;

    public static String BASEURL = "https://api.btcmarkets.net";
    private static final String APIKEY_HEADER = "apikey";
    private static final String TIMESTAMP_HEADER = "timestamp";
    private static final String SIGNATURE_HEADER = "signature";
    private static final String ENCODING = "UTF-8";
    private static final String ALGORITHM = "HmacSHA512";

    public PlaceOrder(String apiKey, String privateKey) throws IOException {
        this(null, apiKey, privateKey);
    }

    public PlaceOrder(String configFile) throws IOException {
        this(configFile, null, null);
    }

    private PlaceOrder(String configFile, String apiKey, String privateKey) throws IOException {
        this.apiKey = apiKey;
        this.privateKey = privateKey;
        if (configFile != null) {
            loadKeys(configFile);
        }
    }

    public void sendRequest(String path, String postData) {
        System.out.println("===Request Start===");
        String response = "";
        try {
            // get the current timestamp. It's best to use ntp or similar services in order to sync
            // your server time
            String timestamp = Long.toString(System.currentTimeMillis());

            // create the string that needs to be signed
            String stringToSign = buildStringToSign(path, null, postData, timestamp);
            System.out.println("===stringToSign Begins===\n" + stringToSign
                    + "\n===stringToSign Ends===");

            // build signature to be included in the http header
            String signature = signRequest(privateKey, stringToSign);
            System.out.println("===signature Begins===\n" + signature + "\n===signature Ends===");

            // full url path
            String url = BASEURL + path;

            response = executeHttpPost(postData, url, apiKey, privateKey, signature, timestamp);
        }
        catch (Exception e) {
            System.out.println(e);
        }
        System.out.println("===response Begins===\n" + response + "\n===response Ends===");
        System.out.println("===Request End===\n");
    }

    private static String executeHttpPost(String postData, String url, String apiKey,
            String privateKey, String signature, String timestamp) throws Exception {
        DefaultHttpClient httpClient = new DefaultHttpClient();
        HttpResponse httpResponse = null;

        try {
            HttpRequestBase request;
            if (postData == null) {
                request = new HttpGet(url);
            } else {
                // post any data that needs to go with http request.
                request = new HttpPost(url);
                ((HttpPost) request).setEntity(new StringEntity(postData, ENCODING));
            }

            // Set http headers
            request.addHeader("Accept", "*/*");
            request.addHeader("Accept-Charset", ENCODING);
            request.addHeader("Content-Type", "application/json");

            // Add signature, timestamp and apiKey to the http header
            request.addHeader(SIGNATURE_HEADER, signature);
            request.addHeader(APIKEY_HEADER, apiKey);
            request.addHeader(TIMESTAMP_HEADER, timestamp);

            // execute http request
            httpResponse = httpClient.execute(request);

            if (httpResponse.getStatusLine().getStatusCode() != 200) {
                System.err.println(httpResponse);
                throw new RuntimeException(httpResponse.getStatusLine().getReasonPhrase());
            }
            // return JSON results as String
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            String responseBody = responseHandler.handleResponse(httpResponse);
            return responseBody;

        }
        catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("unable to execute json call:" + e);
        } finally {
            // close http connection
            if (httpResponse != null) {
                HttpEntity entity = httpResponse.getEntity();
                if (entity != null) {
                    EntityUtils.consume(entity);
                }
            }
            if (httpClient != null) {
                httpClient.getConnectionManager().shutdown();
            }
        }
    }

    private static String buildStringToSign(String uri, String queryString, String postData,
            String timestamp) {
        // queryString must be sorted key=value& pairs
        String stringToSign = uri + "\n";
        if (queryString != null) {
            stringToSign += queryString + "\n";
        }
        stringToSign += timestamp + "\n";
        if (postData != null) {
            stringToSign += postData;
        }
        return stringToSign;
    }

    private static String signRequest(String secret, String data) {
        String signature = "";
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec secret_spec = new SecretKeySpec(Base64.decodeBase64(secret), ALGORITHM);
            mac.init(secret_spec);
            signature = Base64.encodeBase64String(mac.doFinal(data.getBytes()));
        }
        catch (InvalidKeyException e) {
            System.out.println(e);
        }
        catch (NoSuchAlgorithmException e) {
            System.out.println(e);
        }
        catch (Exception e) {
            System.out.println(e);
        }
        return signature;
    }

    private void loadKeys(String configFile) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(configFile));
        String line;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("API_KEY=")) {
                apiKey = line.substring(8);
            } else if (line.startsWith("PRIVATE_KEY=")) {
                privateKey = line.substring(12);
            }
        }
        br.close();
    }

    public static void main(String[] args) throws Exception {
        PlaceOrder p = new PlaceOrder("keys.conf");
        p.sendRequest(
                "/order/create",
                "{\"currency\":\"AUD\",\"instrument\":\"BTC\",\"price\":13000000000,\"volume\":10000000,\"orderSide\":\"Bid\",\"ordertype\":\"Limit\",\"clientRequestId\":\"1\"}");
        p.sendRequest("/order/history",
                "{\"currency\":\"AUD\",\"instrument\":\"BTC\",\"limit\":10,\"since\":1}");
        p.sendRequest("/order/open",
                "{\"currency\":\"AUD\",\"instrument\":\"BTC\",\"limit\":10,\"since\":1}");
        p.sendRequest("/order/trade/history",
                "{\"currency\":\"AUD\",\"instrument\":\"BTC\",\"limit\":10,\"since\":1}");
        p.sendRequest("/account/balance", null);
    }
}
