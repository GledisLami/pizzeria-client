package org.pizzeria;

import lets_make_a_pizza.serveur.Pizzaiolo;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
/**
 * The {@code Client} class represents an MQTT client that interacts with a pizzeria system.
 * It allows customers to request a menu, place orders, and receive order updates via MQTT topics.
 *
 * <p>This class handles communication with the MQTT broker, processes received messages, and
 * manages the order lifecycle.</p>
 *
 * @version 1.0
 */
public class Client {
    private static final Logger logger = LoggerFactory.getLogger(Client.class);
    private static final ConfigReader config = new ConfigReader("app.properties");
    private static final String BROKER_URL = config.getProperty("broker.url");
    private static final String CLIENT_ID = "Customer-" + UUID.randomUUID();
    private static final String ORDER_TOPIC = config.getProperty("order.topic");
    private static final String BCAST_REQUEST_MENU_TOPIC = config.getProperty("bcast.request.menu.topic");
    private static final String BCAST_RETRIEVE_MENU_TOPIC = config.getProperty("bcast.retrieve.menu.topic");
    private static final int QOS = Integer.parseInt(config.getProperty("qos"));

    private final MqttClient client;
    private String orderId;
    public List<Pizzaiolo.DetailsPizza> menuPizzas = new ArrayList<>();
    public String orderStatus = "The pizzeria has not accepted the order yet! Please wait";
    public boolean orderActive = false;
    public boolean orderDelivered = false;
    public boolean orderCancelled = false;

    /**
     * Constructs a new {@code Client} instance and initializes the MQTT client.
     *
     * @throws MqttException if there is an issue creating the MQTT client.
     */
    public Client() throws MqttException {
        this.client = new MqttClient(BROKER_URL, CLIENT_ID, new MemoryPersistence());
    }

    /**
     * Starts the MQTT client, connects to the broker, and subscribes to menu topics.
     *
     * @throws MqttException if an error occurs during connection or subscription.
     */
    public void start() throws MqttException {
        MqttConnectOptions options = getMqttConnectOptions();
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                System.err.println("Connection lost: " + cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String payload = new String(message.getPayload());
                System.out.println("[Customer] Received message: " + payload + " from topic: " + topic);

                if (topic.equals(BCAST_RETRIEVE_MENU_TOPIC)) {
                    handleMenuResponse(payload);
                    return;
                }
                if (topic.contains("/status")) {
                    handleStatusResponse(payload);
                    return;
                }
                if (topic.contains("/delivery")) {
                    handleDeliveryResponse(payload);
                    return;
                }
                if (topic.contains(ORDER_TOPIC) && topic.contains("/cancelled")) {
                    handleOrderCancellation(payload);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                System.out.println("Message delivered: " + token.getMessageId());
            }
        });

        client.connect(options);
        client.subscribe(BCAST_RETRIEVE_MENU_TOPIC, QOS);
    }

    /**
     * Subscribes to order-related MQTT topics to receive status updates, delivery, and cancellations.
     *
     * @throws MqttException if an error occurs during subscription.
     */
    public void subcribeToOrderTopics() throws MqttException {
        client.subscribe(ORDER_TOPIC + orderId + "/status", QOS);
        client.subscribe(ORDER_TOPIC + orderId + "/delivery", QOS);
        client.subscribe(ORDER_TOPIC + orderId + "/cancelled", QOS);
    }

    /**
     * Unsubscribes from order-related MQTT topics.
     *
     * @throws MqttException if an error occurs during unsubscription.
     */
    public void unSubscribeFromOrderTopics() throws MqttException {
        client.unsubscribe(ORDER_TOPIC + orderId + "/status");
        client.unsubscribe(ORDER_TOPIC + orderId + "/delivery");
        client.unsubscribe(ORDER_TOPIC + orderId + "/cancelled");
    }

    /**
     * Handles incoming order status updates.
     *
     * @param payload the status message received from the broker.
     */
    public void handleStatusResponse(String payload) {
        this.orderStatus = payload;
    }

    /**
     * Handles order cancellation messages.
     *
     * @param payload the cancellation message received from the broker.
     */
    public void handleOrderCancellation(String payload) {
        handleStatusResponse(payload);
        this.orderActive = false;
        this.orderId = null;
        this.orderCancelled = true;
    }

    /**
     * Handles delivery confirmation messages.
     *
     * @param payload the delivery confirmation message received.
     */
    public void handleDeliveryResponse(String payload) {
        handleStatusResponse("Number of pizzas delivered: " + payload);
        this.orderDelivered = true;
    }

    /**
     * Confirms that the delivery has been processed.
     */
    public void confirmDelivery() {
        this.orderActive = false;
        this.orderId = null;
        this.orderDelivered = false;
    }

    /**
     * Confirms that the order cancellation has been acknowledged.
     */
    public void confirmCancellation() {
        this.orderCancelled = false;
    }

    /**
     * Returns MQTT connection options.
     *
     * @return an instance of {@code MqttConnectOptions} with predefined settings.
     */
    private static MqttConnectOptions getMqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setConnectionTimeout(60);
        options.setKeepAliveInterval(30);
        options.setAutomaticReconnect(true);
        options.setMaxInflight(50);
        return options;
    }

    /**
     * Publishes a message to a given MQTT topic.
     *
     * @param topic   the topic to publish to.
     * @param message the message payload.
     * @throws MqttException if an error occurs while publishing.
     */
    public void publish(String topic, String message) throws MqttException {
        logger.info("Publishing message: {} to topic: {}", message, topic);
        client.publish(topic, new MqttMessage(message.getBytes()));
    }

    /**
     * Places an order for pizzas by publishing an order message to the order topic.
     *
     * @param pizzas the list of pizzas to order.
     * @throws MqttException if an error occurs while publishing the order.
     */
    public void placeOrder(List<OrderPizza> pizzas) throws MqttException {
        if (pizzas.isEmpty()) {
            return;
        }
        if (this.orderId != null) {
            unSubscribeFromOrderTopics();
        }
        this.orderId = UUID.randomUUID().toString();
        subcribeToOrderTopics();

        String orderPayload = generatePizzaOrderString(pizzas);
        client.publish(ORDER_TOPIC + orderId, new MqttMessage(orderPayload.getBytes()));
        this.orderActive = true;
        System.out.println("Placed order: " + orderPayload);
    }

    /**
     * Generates a formatted pizza order string for transmission over MQTT.
     *
     * @param pizzas the list of pizzas to order.
     * @return a formatted string representing the order.
     */
    public static String generatePizzaOrderString(List<OrderPizza> pizzas) {
        return pizzas.stream()
                .map(pizza -> pizza.getName() + "," + pizza.getQty())
                .collect(Collectors.joining("~"));
    }

    /**
     * Requests the menu from the pizzeria by publishing an empty message to the menu request topic.
     *
     * @throws MqttException if an error occurs while publishing.
     */
    public void requestMenu() throws MqttException {
        publish(BCAST_REQUEST_MENU_TOPIC, "");
        logger.info("Requested menu.");
    }

    /**
     * Handles the response containing the menu items.
     *
     * @param menuData the raw menu data received.
     */
    private void handleMenuResponse(String menuData) {
        if (menuData == null || menuData.isEmpty()) {
            this.menuPizzas = new ArrayList<>();
            return;
        }

        List<Pizzaiolo.DetailsPizza> pizzas = new ArrayList<>();
        for (String line : menuData.split("\n")) {
            String[] parts = line.split("~");
            String name = parts[0];
            List<Pizzaiolo.Ingredient> ingredients = Arrays.stream(parts[1].split(","))
                    .map(String::trim)
                    .map(Pizzaiolo.Ingredient::valueOf)
                    .collect(Collectors.toList());
            int price = Integer.parseInt(parts[2]);

            pizzas.add(new Pizzaiolo.DetailsPizza(name, ingredients, price));
        }

        logger.info("Pizzas retrieved from menu: {}", pizzas);
        this.menuPizzas = pizzas;
    }
}
