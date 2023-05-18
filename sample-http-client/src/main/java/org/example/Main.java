package org.example;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;

import javax.net.ssl.SSLContext;

import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;

public class Main {

    private static final String url = "https://mtls.asga.io:444";
    private static KeyStore keystore;
    private static KeyStore truststore;

    private static CloseableHttpAsyncClient client;

    public static void main(String[] args) throws Exception {

        keystore = getKeyStore("wso2carbon");
        truststore = getKeyStore("client-truststore");

        callAsyncClient();
    }

    private static void callAsyncClient() {
        try {

            CloseableHttpAsyncClient client = getClient();

            long requestStartTime = System.currentTimeMillis();

//            client.execute(createGetRequest(), new FutureCallback<HttpResponse>() {
            client.execute(createPostRequest(), new FutureCallback<HttpResponse>() {
                @Override
                public void completed(final HttpResponse response) {

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responsePhrase = response.getStatusLine().getReasonPhrase();
                    System.out.println("COMPLETED: " + responsePhrase + " " + responsePhrase);
                    Scanner sc = null;
                    try {
                        sc = new Scanner(response.getEntity().getContent());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    while(sc.hasNext()) {
                        System.out.println(sc.nextLine());
                    }
                }

                @Override
                public void failed(final Exception ex) {

                    ex.printStackTrace();
                }

                @Override
                public void cancelled() {

                    System.out.println("Publishing event data to WebSubHub cancelled.");
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static HttpPost createPostRequest() throws UnsupportedEncodingException {

       HttpPost request = new HttpPost(url);
       request.setHeader(ACCEPT, "application/json");
       request.setHeader(CONTENT_TYPE, "application/json");
       String jsonString = "{\"hub.mode\":\"publish\",\"hub.topic\":\"tdorg-test\"}";
       request.setEntity(new StringEntity(jsonString));
       return request;
    }

    private static HttpGet createGetRequest() throws UnsupportedEncodingException {

        HttpGet request = new HttpGet(url);
        return request;
    }

    public static CloseableHttpAsyncClient getClient() throws Exception {

        PoolingNHttpClientConnectionManager connectionManager;
        try {
            connectionManager = createPoolingConnectionManager();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        RequestConfig config = createRequestConfig();
        HttpAsyncClientBuilder httpClientBuilder = HttpAsyncClients.custom().setDefaultRequestConfig(config);
        addSslContext(httpClientBuilder);

        // Set connection manager.
        // MTLS is working without this line.
        httpClientBuilder.setConnectionManager(connectionManager);

        client = httpClientBuilder.build();
        client.start();
        return client;
    }

    private static RequestConfig createRequestConfig() throws UnknownHostException {

        return RequestConfig.custom()
                .setConnectTimeout(300)
                .setConnectionRequestTimeout(300)
                .setSocketTimeout(300)
                .setRedirectsEnabled(false)
                .setRelativeRedirectsAllowed(false)
                .build();
    }

    private static void addSslContext(HttpAsyncClientBuilder builder) throws IOException {

        try {
            SSLContext sslContext = SSLContexts.custom()
                    .loadKeyMaterial(keystore, "wso2carbon".toCharArray(), (aliases, socket) -> "thamindu.io")
                    .loadTrustMaterial(truststore, null)
                    .build();

            builder.setSSLContext(sslContext);
            builder.setSSLHostnameVerifier(new DefaultHostnameVerifier());

        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException | UnrecoverableKeyException e) {
            e.printStackTrace();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static KeyStore getKeyStore(String keystoreName) throws IOException {

        KeyStore keyStore = null;
        try (FileInputStream is = new FileInputStream(keystoreName + ".jks")) {
            keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            char[] pwdArray = "wso2carbon".toCharArray();
            keyStore.load(is, pwdArray);
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            e.printStackTrace();
        }
        return keyStore;
    }

    private static PoolingNHttpClientConnectionManager createPoolingConnectionManager() throws IOException {

        int maxConnections = 20;
        int maxConnectionsPerRoute = 20;

        ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor();
        PoolingNHttpClientConnectionManager poolingHttpClientConnectionMgr = new
                PoolingNHttpClientConnectionManager(ioReactor);
        poolingHttpClientConnectionMgr.setMaxTotal(maxConnections);
        poolingHttpClientConnectionMgr.setDefaultMaxPerRoute(maxConnectionsPerRoute);
        return poolingHttpClientConnectionMgr;
    }
}
