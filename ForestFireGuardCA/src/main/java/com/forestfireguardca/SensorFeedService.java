package com.forestfireguardca;

import io.grpc.stub.StreamObserver;
import java.util.Random;

public class SensorFeedService extends LiveSensorFeedServiceGrpc.LiveSensorFeedServiceImplBase {

    @Override
    public void streamSensorData(SensorRequest request, StreamObserver<SensorData> responseObserver) {
        try {
            // simulate 5 sensor data points over time
            for (int i = 0; i < 5; i++) {
                SensorData data = SensorData.newBuilder()
                        .setSensorId(request.getSensorId())
                        .setLocation(request.getLocation())
                        .setTemperature(20 + new Random().nextFloat() * 10) // value between 20–30
                        .setSmokeDetected(false)
                        .setTimestamp(System.currentTimeMillis())
                        .build();

                // send each data point to the client
                responseObserver.onNext(data);

                // wait 1 second between readings to simulate real-time stream
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // mark the stream as completed
            responseObserver.onCompleted();
        }
    }

    @Override
    public StreamObserver<SensorRequest> monitorSensors(StreamObserver<SensorData> responseObserver) {
        return new StreamObserver<SensorRequest>() {

            @Override
            public void onNext(SensorRequest request) {
                // for each incoming sensor request, reply immediately with one data point
                SensorData data = SensorData.newBuilder()
                        .setSensorId(request.getSensorId())
                        .setLocation(request.getLocation())
                        .setTemperature(25.0f)
                        .setSmokeDetected(true)
                        .setTimestamp(System.currentTimeMillis())
                        .build();

                responseObserver.onNext(data);
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
            }

            @Override
            public void onCompleted() {
                // close the response stream
                responseObserver.onCompleted();
            }
        };
    }
}
