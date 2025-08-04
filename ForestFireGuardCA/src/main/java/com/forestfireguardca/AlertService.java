package com.forestfireguardca;

import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;

public class AlertService extends FireAlertDispatcherServiceGrpc.FireAlertDispatcherServiceImplBase {

    @Override
    public StreamObserver<FireAlert> sendAlerts(StreamObserver<DispatchStatus> responseObserver) {

        return new StreamObserver<FireAlert>() {

            List<FireAlert> alertList = new ArrayList<>();

            @Override
            public void onNext(FireAlert alert) {
                // add each incoming alert to the list
                alertList.add(alert);
            }

            @Override
            public void onError(Throwable t) {
                // just print the error if something goes wrong
                t.printStackTrace();
            }

            @Override
            public void onCompleted() {
                // after receiving all alerts, return a status message
                String detail = "Received " + alertList.size() + " alerts";
                DispatchStatus status = DispatchStatus.newBuilder()
                        .setSuccess(true)
                        .setDetails(detail)
                        .build();

                // send back the confirmation to client
                responseObserver.onNext(status);
                responseObserver.onCompleted();
            }
        };
    }
}
