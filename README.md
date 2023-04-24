### Server Setup
1. Refer https://www.baeldung.com/openssl-self-signed-cert to create a `key` and `crt` for the Mock Server
2. Copy the `key` and `crt` file to the server directory and update the `certFile` and `keyFile` parameters in the `main.bal` with the paths.
3. Extract public cert of client (wso2carbon cert from wso2carbon.jks) and copy it to the server directory.
4. Update `cert` parameter in `main.bal` file with the client crt path.

Run the server using
```
bal run /<root_path>/mtls-mock-server  -- -Cballerina.http.traceLogConsole=true
```
to enable HTTP logs if necessary.

### Client Setup
1. Copy the IS keystore and truststore to the client root directory. 
2. Refer https://access.redhat.com/documentation/en-us/red_hat_jboss_data_virtualization/6.2/html/security_guide/extract_a_self-signed_certificate_from_the_keystore to extract the crt with alias `wso2carbon`. Use this crt in the server setup.
3. Import the Server crt to the truststore

Run the client with VM options
```
-Djavax.net.debug=ssl:handshake
```
to enable SSL handshake logs.
