import ballerina/http;
import ballerina/log;


// type Album readonly & record {|
//     string title;
//     string artist;
// |};

// An HTTP listener can be configured to accept new connections that are secured via mutual SSL.
listener http:Listener securedEP = new (9090,
    secureSocket = {
        key: {
            certFile: "../mtls.crt",
            keyFile: "../mtls.key"
        },
        // Enables mutual SSL.
        mutualSsl: {
            verifyClient: http:REQUIRE,
            cert: "../asg.crt"
        }
    }
);

service / on securedEP {
// service / on new http:Listener(9091) {

    resource function post hub(@http:Payload json message) returns http:Ok {

        log:printInfo("Received event: ", payload = message);

        http:Ok ok = { body: "Success"};
        return ok;
    }

    // resource function post onRegisterTopic(readonly & websubhub:TopicRegistration message)
    //                             returns websubhub:TopicRegistrationSuccess|websubhub:TopicRegistrationError {
                                
    //     log:printInfo("Received topic registration: ", payload = message);
    //     log:printInfo("Topic registered.");
    //     return websubhub:TOPIC_REGISTRATION_SUCCESS;
    // }
}
