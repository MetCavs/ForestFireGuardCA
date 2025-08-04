package com.forestfireguardca;

import io.grpc.stub.StreamObserver;

public class FireDetectService extends FireDetectionServiceGrpc.FireDetectionServiceImplBase {

    @Override
    public void checkFire(FireSensorRequest req, StreamObserver<FireAlert> resp) {
        // get values from the request
        String sensorId = req.getSensorId();
        float temp = req.getTemperature();
        boolean smoke = req.getSmokeDetected();
        String location = req.getLocation();

        // decide if there is fire based on simple logic
        boolean fireDetected = (temp > 37.0f && smoke);

        String level = fireDetected ? "HIGH" : "NORMAL";
        String message = fireDetected ? "Fire detected!" : " All clear";

        // create the response message
        FireAlert alert = FireAlert.newBuilder()
                .setSensorId(sensorId)
                .setFireDetected(fireDetected)
                .setAlertLevel(level)
                .setMessage(message)
                .setTimestamp(System.currentTimeMillis())
                .build();

        // send the response to the client
        resp.onNext(alert);
        resp.onCompleted();
    }
}
