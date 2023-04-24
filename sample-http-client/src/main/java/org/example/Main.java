package org.example;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.commons.lang.StringUtils;
import org.apache.http.util.EntityUtils;

import javax.net.ssl.SSLContext;

import static org.apache.http.HttpHeaders.ACCEPT;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;

public class Main {

    private static CloseableHttpAsyncClient client;
    public static void main(String[] args) {

        String url = "https://mtlstest:9090/hub";
        try {
            HttpPost request = new HttpPost(url);
            request.setHeader(ACCEPT, "application/json");
            request.setHeader(CONTENT_TYPE, "application/json");

            String jsonString = "{\"hub.mode\":\"publish\",\"hub.topic\":\"https://localhost:9443/oauth2/token\",\"hub.url\":\"https://localhost:9443/oauth2/token\"}";

            request.setEntity(new StringEntity(jsonString));
            CloseableHttpAsyncClient client = getClient();

            long requestStartTime = System.currentTimeMillis();
            client.execute(request, new FutureCallback<HttpResponse>() {
                @Override
                public void completed(final HttpResponse response) {

                    int responseCode = response.getStatusLine().getStatusCode();
                    String responsePhrase = response.getStatusLine().getReasonPhrase();

                    System.out.println(responsePhrase + " " + responsePhrase);
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

            KeyStore keyStore = getKeyStore("wso2carbon");
            KeyStore trustStore = getKeyStore("client-truststore");
//            ServerConfigurationService config = WebSubHubEventPublisherDataHolder.getInstance().getServerConfigurationService();
//            String password = config
//                    .getFirstProperty(RegistryResources.SecurityManagement.SERVER_PRIMARY_KEYSTORE_PASSWORD);
            SSLContext sslContext = SSLContexts.custom()
                    .loadKeyMaterial(keyStore, "wso2carbon".toCharArray())
                    .loadTrustMaterial(trustStore, null)

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
}