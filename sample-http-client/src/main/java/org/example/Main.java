package org.example;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang.ArrayUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.commons.lang.StringUtils;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;

public class Main {

//    private static final String url = "https://mtls.asga.io";
    private static final String url = "https://mtlstest:9090/hub";
    private static KeyStore keystore;
    private static KeyStore truststore;

    private static CloseableHttpAsyncClient client;
    public static void main(String[] args) throws Exception {
        keystore = getKeyStore("wso2carbon");
        truststore = getKeyStore("client-truststore");

        callAsyncClient();
//        callSyncClient();
//        callOkHttpClient();
    }

    private static void callAsyncClient() {
        try {

            CloseableHttpAsyncClient client = getClient();

            long requestStartTime = System.currentTimeMillis();
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

    public static void callSyncClient() throws Exception {

        SSLContext sslContext = SSLContexts.custom()
                .loadKeyMaterial(keystore, "wso2carbon".toCharArray())
                .loadTrustMaterial(truststore, null)
                .build();

        HttpClient httpClient = HttpClients.custom().setSSLContext(sslContext).build();
        HttpResponse response = httpClient.execute(createPostRequest());
        System.out.println(response.getStatusLine().getStatusCode());
        HttpEntity entity = response.getEntity();

        System.out.println("----------------------------------------");
        System.out.println(response.getStatusLine());
        EntityUtils.consume(entity);
    }

    private static HttpPost createPostRequest() throws UnsupportedEncodingException {
        HttpPost request = new HttpPost(url);
        request.setHeader(ACCEPT, "application/json");
        request.setHeader(CONTENT_TYPE, "application/json");

        String jsonString = "{\"hub.mode\":\"publish\",\"hub.topic\":\"https://localhost:9443/oauth2/token\",\"hub.url\":\"https://localhost:9443/oauth2/token\"}";

        request.setEntity(new StringEntity(jsonString));
        return request;
    }

    private static HttpGet createGetRequest() throws UnsupportedEncodingException {
        HttpGet request = new HttpGet(url);
//        request.setHeader(ACCEPT, "application/json");
//        request.setHeader(CONTENT_TYPE, "application/json");

        return request;
    }

    public static CloseableHttpAsyncClient getClient() throws Exception {

        RequestConfig config = createRequestConfig();
        HttpAsyncClientBuilder httpClientBuilder = HttpAsyncClients.custom().setDefaultRequestConfig(config);
        addSslContext(httpClientBuilder);
//        httpClientBuilder.setConnectionManager(createPoolingConnectionManager());
        client = httpClientBuilder.build();
        client.start();
        return client;
    }

    private static PoolingNHttpClientConnectionManager createPoolingConnectionManager() throws Exception {

        String maxConnectionsString = "20";
        String maxConnectionsPerRouteString = "20";
        int maxConnections = 20;
        int maxConnectionsPerRoute = 20;

        if (StringUtils.isNotEmpty(maxConnectionsString)) {
            try {
                maxConnections = Integer.parseInt(maxConnectionsString);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }

        if (StringUtils.isNotEmpty(maxConnectionsPerRouteString)) {
            try {
                maxConnectionsPerRoute = Integer.parseInt(maxConnectionsPerRouteString);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }

        ConnectingIOReactor ioReactor;
        try {
            ioReactor = new DefaultConnectingIOReactor();
            PoolingNHttpClientConnectionManager poolingHttpClientConnectionManager = new
                    PoolingNHttpClientConnectionManager(ioReactor);
            // Increase max total connection to 20.
            poolingHttpClientConnectionManager.setMaxTotal(maxConnections);
            // Increase default max connection per route to 20.
            poolingHttpClientConnectionManager.setDefaultMaxPerRoute(maxConnectionsPerRoute);
            return poolingHttpClientConnectionManager;
        } catch (IOReactorException e) {
            e.printStackTrace();
            throw new Exception("Failed to create the connection manager.", e);
        }

    }

    private static RequestConfig createRequestConfig() {

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
//            ServerConfigurationService config = WebSubHubEventPublisherDataHolder.getInstance().getServerConfigurationService();
//            String password = config
//                    .getFirstProperty(RegistryResources.SecurityManagement.SERVER_PRIMARY_KEYSTORE_PASSWORD);
            SSLContext sslContext = SSLContexts.custom()
                    .loadKeyMaterial(keystore, "wso2carbon".toCharArray())
                    .loadTrustMaterial(truststore, null)
                    .build();
            builder.setSSLContext(sslContext);
//            builder.setSSLStrategy(new SSLIOSessionStrategy(sslContext, new String[]{"TLSv1.2"}, null,
//                    SSLIOSessionStrategy.getDefaultHostnameVerifier()));
            builder.setSSLHostnameVerifier(new DefaultHostnameVerifier());

        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException | UnrecoverableKeyException e) {
            e.printStackTrace();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static KeyStore getKeyStore(String keystoreName) throws IOException {

        KeyStore keyStore = null;
        try (FileInputStream is = new FileInputStream(keystoreName + ".jks")){
            keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            char[] pwdArray = "wso2carbon".toCharArray();
            keyStore.load(is, pwdArray);
//            ks.load(Main.class.getClassLoader().getResourceAsStream(keystoreName + ".jks"), pwdArray);
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            e.printStackTrace();
        }
        return keyStore;
    }

    public static void callOkHttpClient() throws Exception {

        try {
            OkHttpClient okHttpClient = getOkHttpClientForMTLS();
            Request request = new Request.Builder().url(url)
                    .header("Accept", "application/json")
                    .build();
            try (Response response = okHttpClient.newCall(request).execute()) {
                System.out.println(response.code() + " " + response.message());
//                System.out.println(response.body().);

                System.out.println("----------------------------------------");
//                System.out.println(response.getStatusLine());
            }
        } catch (IOException e) {
            throw new Exception("Error while making the GET call to: ", e);
        }
    }
    private static OkHttpClient getOkHttpClientForMTLS() throws Exception {

        try {
            Long callTimeOut = 30L;
            Long connectTimeOut = 10L;
            Long readTimeOut = 30L;
            Long writeTimeOut = 30L;

            final SSLContext sslContext;
            sslContext = SSLContext.getInstance("TLS");

            TrustManager[] trustManagers = getTrustManagers();
            KeyManager[] keyManagers = getKeyManagers();
            sslContext.init(keyManagers, trustManagers, new java.security.SecureRandom());
            // Create a ssl socket factory.
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            if (ArrayUtils.isEmpty(trustManagers)) {
                throw new Exception("Trust Managers array is empty");
            }

            if (!(trustManagers[0] instanceof X509TrustManager)) {
                throw new Exception("Trust Manager is not a instance of X509TrustManager.");
            }

            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustManagers[0])
                    .callTimeout(callTimeOut, TimeUnit.SECONDS)
                    .connectTimeout(connectTimeOut, TimeUnit.SECONDS)
                    .readTimeout(readTimeOut, TimeUnit.SECONDS)
                    .writeTimeout(writeTimeOut, TimeUnit.SECONDS)
                    .hostnameVerifier((hostname, session) -> true).build();
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new Exception("Error initiating the client.", e);
        }
    }

    public static KeyManager[] getKeyManagers() throws Exception {

        try {
            KeyManagerFactory keyManagerFactory =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keystore, "wso2carbon".toCharArray());
            return keyManagerFactory.getKeyManagers();
        } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException e) {
            throw new Exception("Error initiating the Key Managers", e);
        }
    }

    public static TrustManager[] getTrustManagers() throws Exception {

        try {
            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(truststore);
            return trustManagerFactory.getTrustManagers();
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            throw new Exception("Error initiating the Trust Managers", e);
        }
    }
}