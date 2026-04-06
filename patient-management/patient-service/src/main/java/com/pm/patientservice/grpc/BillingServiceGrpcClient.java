package com.pm.patientservice.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import billing.BillingServiceGrpc;

@Service
public class BillingServiceGrpcClient {
    private final BillingServiceGrpc.BillingServiceBlockingStub billingServiceStub;
    private final Logger log = LoggerFactory.getLogger(BillingServiceGrpcClient.class);

    public BillingServiceGrpcClient(
            @Value("${billing.service.address:localhost}") String host,
            @Value("${billing.service.grpc.port:9001}") int port
    ){
        log.info("Initializing BillingServiceGrpcClient with host: {} and port: {}", host, port);
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        billingServiceStub = BillingServiceGrpc.newBlockingStub(channel);
    }

    public billing.BillingResponse createBillingAccount(String patientId, String patientName, String email){
        log.info("Sending billing request to BillingService via GRPC: {}, {}, {}", patientId, patientName, email);
        billing.BillingRequest request = billing.BillingRequest.newBuilder()
                .setPatientId(patientId)
                .setName(patientName)
                .setEmail(email)
                .build();
        billing.BillingResponse response = billingServiceStub.createBillingAccount(request);
        log.info("Received billing response from BillingService via GRPC: {}", response);
        return response;
    }
}
