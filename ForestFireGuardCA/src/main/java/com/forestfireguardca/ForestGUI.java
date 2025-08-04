package com.forestfireguardca;

import javax.swing.*;
import java.net.InetAddress;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.awt.Image;

public class ForestGUI {

    public ForestGUI() {
        try {
            // main loop: keep asking the user until they type "exit"
            while (true) {
                String action = getActionFromUser(); // ask user what they want to do
                if (action == null) break;

                action = action.trim().toLowerCase();

                // call the correct method based on input
                if (action.equals("check fire")) {
                    handleCheckFire();
                } else if (action.equals("stream sensor")) {
                    handleStreamSensor();
                } else if (action.equals("send alerts")) {
                    handleSendAlerts();
                } else if (action.equals("exit")) {
                    break; // exit the app
                } else {
                    JOptionPane.showMessageDialog(null, "Unknown command: " + action);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "An error occurred.");
        }
    }

    private String getActionFromUser() {
        // left side with text and input field
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.add(new JLabel("What would you like to do?"));
        leftPanel.add(new JLabel("- check fire"));
        leftPanel.add(new JLabel("- stream sensor"));
        leftPanel.add(new JLabel("- send alerts"));
        leftPanel.add(new JLabel("- exit"));

        JTextField inputField = new JTextField();
        leftPanel.add(Box.createVerticalStrut(10));
        leftPanel.add(inputField);

        // right side with app icon
        JPanel rightPanel = new JPanel();
        ImageIcon icon = new ImageIcon("src/main/image/icon.png");
        Image scaled = icon.getImage().getScaledInstance(64, 64, Image.SCALE_SMOOTH);
        JLabel iconLabel = new JLabel(new ImageIcon(scaled));
        rightPanel.add(iconLabel);

        // combine left and right panels
        JPanel fullPanel = new JPanel();
        fullPanel.setLayout(new BoxLayout(fullPanel, BoxLayout.X_AXIS));
        fullPanel.add(leftPanel);
        fullPanel.add(Box.createHorizontalStrut(15));
        fullPanel.add(rightPanel);

        // show the input dialog
        int result = JOptionPane.showConfirmDialog(null, fullPanel, "ForestFireGuard",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            return inputField.getText().trim().toLowerCase();
        } else {
            return "exit"; // treat cancel as exit
        }
    }

    private void handleCheckFire() {
        try {
            // discover fire detection service
            ServiceInfo serviceInfo = discoverService("_fire._tcp.local.", "FireDetection");
            if (serviceInfo == null) return;

            // connect to gRPC server
            ManagedChannel channel = ManagedChannelBuilder
                    .forAddress(serviceInfo.getHostAddresses()[0], serviceInfo.getPort())
                    .usePlaintext()
                    .build();

            // create the stub
            FireDetectionServiceGrpc.FireDetectionServiceBlockingStub stub =
                    FireDetectionServiceGrpc.newBlockingStub(channel);

            // build the request
            FireSensorRequest request = FireSensorRequest.newBuilder()
                    .setSensorId("sensor-101")
                    .setLocation("Sector-A")
                    .setTemperature(38.5f)
                    .setSmokeDetected(true)
                    .setTimestamp(System.currentTimeMillis())
                    .setUserNote("Auto-check")
                    .build();

            // call the service and get response
            FireAlert response = stub.checkFire(request);

            // show result in a popup
            JOptionPane.showMessageDialog(null,
                    "Sensor: " + response.getSensorId() +
                            "\nFire Detected: " + response.getFireDetected() +
                            "\nAlert Level: " + response.getAlertLevel() +
                            "\nMessage: " + response.getMessage());

            channel.shutdown(); // close the connection
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Check Fire failed.");
        }
    }

    private void handleStreamSensor() {
        new Thread(() -> {
            try {
                // discover the live sensor feed service
                ServiceInfo serviceInfo = discoverService("_feed._tcp.local.", "SensorFeed");
                if (serviceInfo == null) {
                    JOptionPane.showMessageDialog(null, "SensorFeed service not found.");
                    return;
                }

                // ask user to select sensor ID
                String sensorId = (String) JOptionPane.showInputDialog(
                        null,
                        "Select Sensor ID:",
                        "Sensor Stream",
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        new String[]{"sensor-101", "sensor-202", "sensor-303"},
                        "sensor-101"
                );
                if (sensorId == null) return;

                // ask user to select location
                String location = (String) JOptionPane.showInputDialog(
                        null,
                        "Select Sensor Location:",
                        "Sensor Location",
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        new String[]{"Sector-A", "Sector-B", "Sector-C"},
                        "Sector-A"
                );
                if (location == null) return;

                // connect to the server
                ManagedChannel channel = ManagedChannelBuilder
                        .forAddress(serviceInfo.getHostAddresses()[0], serviceInfo.getPort())
                        .usePlaintext()
                        .build();

                // create the stub
                LiveSensorFeedServiceGrpc.LiveSensorFeedServiceBlockingStub stub =
                        LiveSensorFeedServiceGrpc.newBlockingStub(channel);

                // build the request
                SensorRequest request = SensorRequest.newBuilder()
                        .setSensorId(sensorId)
                        .setLocation(location)
                        .build();

                // create live feed window
                JTextArea textArea = new JTextArea();
                textArea.setEditable(false);
                JFrame frame = new JFrame("Live Sensor Feed");
                frame.add(new JScrollPane(textArea));
                frame.setSize(400, 300);
                frame.setLocationRelativeTo(null);
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setVisible(true);

                // receive data from server
                stub.streamSensorData(request).forEachRemaining(data -> {
                    String line = "Sensor: " + data.getSensorId() +
                            " | Temp: " + data.getTemperature() +
                            " | Smoke: " + data.getSmokeDetected() +
                            " | Time: " + data.getTimestamp() + "\n";
                    SwingUtilities.invokeLater(() -> textArea.append(line));
                });

                channel.shutdown();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "An error occurred while streaming sensor data.");
            }
        }).start();
    }

    private void handleSendAlerts() {
        try {
            // discover alert dispatcher service
            ServiceInfo serviceInfo = discoverService("_alert._tcp.local.", "AlertDispatcher");
            if (serviceInfo == null) return;

            // connect to the server
            ManagedChannel channel = ManagedChannelBuilder
                    .forAddress(serviceInfo.getHostAddresses()[0], serviceInfo.getPort())
                    .usePlaintext()
                    .build();

            // create stub
            FireAlertDispatcherServiceGrpc.FireAlertDispatcherServiceStub stub =
                    FireAlertDispatcherServiceGrpc.newStub(channel);

            // define how to handle response
            StreamObserver<DispatchStatus> responseObserver = new StreamObserver<DispatchStatus>() {
                @Override
                public void onNext(DispatchStatus status) {
                    JOptionPane.showMessageDialog(null, "Dispatch Result: " + status.getDetails());
                }

                @Override
                public void onError(Throwable t) {
                    JOptionPane.showMessageDialog(null, "Send Alerts failed.");
                }

                @Override
                public void onCompleted() {
                    channel.shutdown();
                }
            };

            // send 3 fake alerts
            StreamObserver<FireAlert> requestObserver = stub.sendAlerts(responseObserver);
            for (int i = 1; i <= 3; i++) {
                FireAlert alert = FireAlert.newBuilder()
                        .setSensorId("sensor-" + i)
                        .setFireDetected(true)
                        .setAlertLevel("WARNING")
                        .setMessage("Alert " + i)
                        .setTimestamp(System.currentTimeMillis())
                        .build();
                requestObserver.onNext(alert);
            }

            requestObserver.onCompleted(); // finish the stream
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Send Alerts failed.");
        }
    }

    private ServiceInfo discoverService(String type, String name) {
        try {
            // discover the service using jmDNS
            JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());
            return jmdns.getServiceInfo(type, name, 3000);
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) {
        new ForestGUI(); // run the app
    }
}
