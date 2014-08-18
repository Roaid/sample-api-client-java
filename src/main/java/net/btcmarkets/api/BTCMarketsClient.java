package net.btcmarkets.api;

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

public class BTCMarketsClient {
    private String apiKey;
    private String privateKey;

    public static boolean DEBUG = false;

    private static final String BASEURL = "https://api.btcmarkets.net";
    private static final String ORDER_CREATE_PATH = "/order/create";
    private static final String ORDER_HISTORY_PATH = "/order/history";
    private static final String ORDER_OPEN_PATH = "/order/open";
    private static final String ORDER_TRADE_HISTORY_PATH = "/order/trade/history";
    private static final String ACCOUNT_BALANCE_PATH = "/account/balance";
    private static final String APIKEY_HEADER = "apikey";
    private static final String TIMESTAMP_HEADER = "timestamp";
    private static final String SIGNATURE_HEADER = "signature";
    private static final String ENCODING = "UTF-8";
    private static final String ALGORITHM = "HmacSHA512";

    public BTCMarketsClient(String apiKey, String privateKey) throws IOException {
        this(null, apiKey, privateKey);
    }

    public BTCMarketsClient(String configFile) throws IOException {
        this(configFile, null, null);
    }

    private BTCMarketsClient(String configFile, String apiKey, String privateKey)
            throws IOException {
        this.apiKey = apiKey;
        this.privateKey = privateKey;
        if (configFile != null) {
            loadKeys(configFile);
        }
    }

    private void debug(String msg) {
        if (DEBUG) {
            System.out.println(msg);
        }
    }

    public String sendRequest(String path, String postData) {
        debug("===Request Start===");
        String response = "";
        try {
            // get the current timestamp. It's best to use ntp or similar services in order to sync
            // your server time
            String timestamp = Long.toString(System.currentTimeMillis());

            // create the string that needs to be signed
            String stringToSign = buildStringToSign(path, null, postData, timestamp);
            debug("===stringToSign Begins===\n" + stringToSign + "\n===stringToSign Ends===");

            // build signature to be included in the http header
            String signature = signRequest(privateKey, stringToSign);
            debug("===signature Begins===\n" + signature + "\n===signature Ends===");

            // full url path
            String url = BASEURL + path;

            response = executeHttpPost(postData, url, apiKey, privateKey, signature, timestamp);
        }
        catch (Exception e) {
            System.err.println(e);
        }
        debug("===response Begins===\n" + response + "\n===response Ends===");
        debug("===Request End===\n");
        return response;
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
            System.err.println(e);
        }
        catch (NoSuchAlgorithmException e) {
            System.err.println(e);
        }
        catch (Exception e) {
            System.err.println(e);
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

    public String accountBalance() {
        return sendRequest(ACCOUNT_BALANCE_PATH, null);
    }

    public String orderHistory(String currency, String instrument, int limit, int since) {
        return sendRequest(ORDER_HISTORY_PATH, getOrderString(currency, instrument, limit, since));
    }

    public String orderHistory() {
        return sendRequest(ORDER_HISTORY_PATH, getDefaultOrderString());
    }

    public String orderOpen(String currency, String instrument, int limit, int since) {
        return sendRequest(ORDER_OPEN_PATH, getOrderString(currency, instrument, limit, since));
    }

    public String orderOpen() {
        return sendRequest(ORDER_OPEN_PATH, getDefaultOrderString());
    }

    public String orderTradeHistory(String currency, String instrument, int limit, int since) {
        return sendRequest(ORDER_TRADE_HISTORY_PATH,
                getOrderString(currency, instrument, limit, since));
    }

    public String orderTradeHistory() {
        return sendRequest(ORDER_TRADE_HISTORY_PATH, getDefaultOrderString());
    }

    private String getDefaultOrderString() {
        return getOrderString("AUD", "BTC", 10, 1);
    }

    private String getOrderString(String currency, String instrument, int limit, int since) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"currency\":\"");
        sb.append(currency);
        sb.append("\",\"instrument\":\"");
        sb.append(instrument);
        sb.append("\",\"limit\":");
        sb.append(limit);
        sb.append(",\"since\":");
        sb.append(since);
        sb.append("}");
        return sb.toString();
    }

    public String createNewOrder(String currency, String instrument, long price, int volume,
            String orderSide, String orderType, String clientRequestId) {
        return sendRequest(
                ORDER_CREATE_PATH,
                getNewOrderString(currency, instrument, price, volume, orderSide, orderType,
                        clientRequestId));
    }

    private String getNewOrderString(String currency, String instrument, long price, int volume,
            String orderSide, String orderType, String clientRequestId) {
        // "{\"currency\":\"AUD\",\"instrument\":\"BTC\",\"price\":13000000000,\"volume\":10000000,\"orderSide\":\"Bid\",\"ordertype\":\"Limit\",\"clientRequestId\":\"1\"}");
        StringBuilder sb = new StringBuilder();
        sb.append("{\"currency\":\"");
        sb.append(currency);
        sb.append("\",\"instrument\":\"");
        sb.append(instrument);
        sb.append("\",\"price\":");
        sb.append(price);
        sb.append(",\"volume\":");
        sb.append(volume);
        sb.append(",\"orderSide\":\"");
        sb.append(orderSide);
        sb.append("\",\"ordertype\":\"");
        sb.append(orderType);
        sb.append("\",\"clientRequestId\":\"");
        sb.append(clientRequestId);
        sb.append("\"}");
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        BTCMarketsClient client = new BTCMarketsClient("keys.conf");
        System.out.println(client.orderHistory("AUD", "BTC", 10, 1));
        System.out.println(client.orderHistory());
        System.out.println(client.orderOpen("AUD", "BTC", 10, 1));
        System.out.println(client.orderOpen());
        System.out.println(client.orderTradeHistory("AUD", "BTC", 10, 1));
        System.out.println(client.orderTradeHistory());
        System.out.println(client.accountBalance());
        System.out.println(client.createNewOrder("AUD", "BTC", 13000000000L, 10000000, "Buy",
                "Limit", "1"));
    }
}
