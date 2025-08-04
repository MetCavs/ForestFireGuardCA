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
            while (true) {
                String action = getActionFromUser();
                if (action == null) break;

                action = action.trim().toLowerCase();

               
                if (action.equals("check fire")) {
                    handleCheckFire();
                } else if (action.equals("stream sensor")) {
                    handleStreamSensor();
                } else if (action.equals("send alerts")) {
                    handleSendAlerts();
                } else if (action.equals("exit")) {
                    break;
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
    // Create label section
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

    // Create right panel with icon
    JPanel rightPanel = new JPanel();
    ImageIcon icon = new ImageIcon("src/main/image/icon.png");
    Image scaled = icon.getImage().getScaledInstance(64, 64, Image.SCALE_SMOOTH);
    JLabel iconLabel = new JLabel(new ImageIcon(scaled));
    rightPanel.add(iconLabel);

    // Combine left and right panels
    JPanel fullPanel = new JPanel();
    fullPanel.setLayout(new BoxLayout(fullPanel, BoxLayout.X_AXIS));
    fullPanel.add(leftPanel);
    fullPanel.add(Box.createHorizontalStrut(15));
    fullPanel.add(rightPanel);

    int result = JOptionPane.showConfirmDialog(null, fullPanel, "ForestFireGuard",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

    if (result == JOptionPane.OK_OPTION) {
        return inputField.getText().trim().toLowerCase();
    } else {
        return "exit";
    }
}


    private void handleCheckFire() {
        try {
            ServiceInfo serviceInfo = discoverService("_fire._tcp.local.", "FireDetection");
            if (serviceInfo == null) return;

            ManagedChannel channel = ManagedChannelBuilder
                    .forAddress(serviceInfo.getHostAddresses()[0], serviceInfo.getPort())
                    .usePlaintext()
                    .build();

            FireDetectionServiceGrpc.FireDetectionServiceBlockingStub stub =
                    FireDetectionServiceGrpc.newBlockingStub(channel);

            FireSensorRequest request = FireSensorRequest.newBuilder()
                    .setSensorId("sensor-101")
                    .setLocation("Sector-A")
                    .setTemperature(38.5f)
                    .setSmokeDetected(true)
                    .setTimestamp(System.currentTimeMillis())
                    .setUserNote("Auto-check")
                    .build();

            FireAlert response = stub.checkFire(request);

            JOptionPane.showMessageDialog(null,
                    "Sensor: " + response.getSensorId() +
                            "\nFire Detected: " + response.getFireDetected() +
                            "\nAlert Level: " + response.getAlertLevel() +
                            "\nMessage: " + response.getMessage());

            channel.shutdown();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Check Fire failed.");
        }
    }

    private void handleStreamSensor() {
    new Thread(() -> {
        try {
            // first we try to discover the SensorFeed service
            ServiceInfo serviceInfo = discoverService("_feed._tcp.local.", "SensorFeed");
            if (serviceInfo == null) {
                JOptionPane.showMessageDialog(null, "SensorFeed service not found.");
                return;
            }

            // let the user select sensor id
            String sensorId = (String) JOptionPane.showInputDialog(
                    null,
                    "Select Sensor ID:",
                    "Sensor Stream",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    new String[]{"sensor-101", "sensor-202", "sensor-303"},
                    "sensor-101"
            );

            if (sensorId == null) return; // user canceled

            // let the user select location
            String location = (String) JOptionPane.showInputDialog(
                    null,
                    "Select Sensor Location:",
                    "Sensor Location",
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    new String[]{"Sector-A", "Sector-B", "Sector-C"},
                    "Sector-A"
            );

            if (location == null) return; // user canceled

            // open channel with host/port from jmDNS result
            ManagedChannel channel = ManagedChannelBuilder
                    .forAddress(serviceInfo.getHostAddresses()[0], serviceInfo.getPort())
                    .usePlaintext()
                    .build();

            // create stub to call server-streaming RPC
            LiveSensorFeedServiceGrpc.LiveSensorFeedServiceBlockingStub stub =
                    LiveSensorFeedServiceGrpc.newBlockingStub(channel);

            // build request using selected values
            SensorRequest request = SensorRequest.newBuilder()
                    .setSensorId(sensorId)
                    .setLocation(location)
                    .build();

            // create window to display the live data
            JTextArea textArea = new JTextArea();
            textArea.setEditable(false);
            JFrame frame = new JFrame("Live Sensor Feed");
            frame.add(new JScrollPane(textArea));
            frame.setSize(400, 300);
            frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setVisible(true);

            // start receiving streamed data from the server
            stub.streamSensorData(request).forEachRemaining(data -> {
                String line = "Sensor: " + data.getSensorId() +
                        " | Temp: " + data.getTemperature() +
                        " | Smoke: " + data.getSmokeDetected() +
                        " | Time: " + data.getTimestamp() + "\n";

                SwingUtilities.invokeLater(() -> textArea.append(line));
            });

            channel.shutdown();

        } catch (Exception e) {
            e.printStackTrace(); // log to terminal
            JOptionPane.showMessageDialog(null, "An error occurred while streaming sensor data.");
        }
    }).start();
}


    private void handleSendAlerts() {
        try {
            ServiceInfo serviceInfo = discoverService("_alert._tcp.local.", "AlertDispatcher");
            if (serviceInfo == null) return;

            ManagedChannel channel = ManagedChannelBuilder
                    .forAddress(serviceInfo.getHostAddresses()[0], serviceInfo.getPort())
                    .usePlaintext()
                    .build();

            FireAlertDispatcherServiceGrpc.FireAlertDispatcherServiceStub stub =
                    FireAlertDispatcherServiceGrpc.newStub(channel);

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

            requestObserver.onCompleted();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Send Alerts failed.");
        }
    }

    private ServiceInfo discoverService(String type, String name) {
        try {
            JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());
            return jmdns.getServiceInfo(type, name, 3000);
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) {
        new ForestGUI();
    }
}
