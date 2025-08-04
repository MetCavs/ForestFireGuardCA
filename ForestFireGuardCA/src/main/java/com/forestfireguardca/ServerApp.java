package com.forestfireguardca;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.net.InetAddress;

public class ServerApp {
    public static void main(String[] args) {
        try {
            // start the gRPC server on port 50051 and register services
            Server server = ServerBuilder.forPort(50051)
                    .addService(new FireDetectService())   // unary RPC
                    .addService(new AlertService())        // client-streaming RPC
                    .addService(new SensorFeedService())   // server/bidi streaming RPC
                    .build()
                    .start();

            System.out.println("Server started on port 50051");

            // make services discoverable via jmDNS
            JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());

            jmdns.registerService(ServiceInfo.create(
                    "_fire._tcp.local.", "FireDetection", 50051, "path=/fire"));

            jmdns.registerService(ServiceInfo.create(
                    "_alert._tcp.local.", "AlertDispatcher", 50051, "path=/alert"));

            jmdns.registerService(ServiceInfo.create(
                    "_feed._tcp.local.", "SensorFeed", 50051, "path=/feed"));

            // keep server alive
            server.awaitTermination();

        } catch (Exception e) {
            System.err.println(" Server failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
