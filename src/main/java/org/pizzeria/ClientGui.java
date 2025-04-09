package org.pizzeria;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import lets_make_a_pizza.serveur.Pizzaiolo;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A JavaFX application that simulates a pizza ordering system where users can view the menu, place orders,
 * and check the status of their active orders.
 * <p>
 * This class provides the GUI interface for interacting with a client-side application that connects to a pizzeria server.
 * It offers screens for viewing the menu, placing orders, and checking the status of active orders.
 */
public class ClientGui extends Application {
    private final Logger log = LoggerFactory.getLogger(ClientGui.class);
    private Client client;
    private Stage primaryStage;

    /**
     * Launches the JavaFX application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Initializes the client and starts requesting the menu asynchronously to provide a smooth user experience.
     * Displays the home screen.
     *
     * @param primaryStage the primary stage for this application
     * @throws MqttException if there is an error communicating with the MQTT server
     */
    @Override
    public void start(Stage primaryStage) throws MqttException {
        this.client = new Client();
        client.start();
        client.requestMenu();
        this.primaryStage = primaryStage;
        showHomeScreen();
    }

    // HOME SCREEN

    /**
     * Displays the home screen with a welcome message and buttons to view the menu or check the order status.
     * If an order is active or cancelled, the status button will be displayed.
     */
    private void showHomeScreen() {
        try {
            client.requestMenu();
        } catch (Exception e) {
            log.error(e.toString());
        }
        VBox root = new VBox(20);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-alignment: center;");

        Label welcomeLabel = new Label("Welcome to the Pizza Ordering App");
        Button viewMenuButton = new Button("View Menu");

        viewMenuButton.setOnAction(event -> showMenuScreen());

        Button viewStatusButton = new Button("View Active Order Status");
        viewStatusButton.setOnAction(actionEvent -> showStatusScreen());

        root.getChildren().addAll(welcomeLabel, viewMenuButton);

        if (client.orderActive || client.orderCancelled) {
            root.getChildren().add(viewStatusButton);
        }
        primaryStage.setScene(new Scene(root, 1000, 400));
        primaryStage.setTitle("Pizza App");
        primaryStage.show();
    }

    // MENU SCREEN

    /**
     * Displays the menu screen where users can view available pizzas and input quantities for ordering.
     * The user can place an order from this screen.
     */
    private void showMenuScreen() {
        List<Pizzaiolo.DetailsPizza> pizzas = client.menuPizzas;

        VBox root = new VBox(10);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-alignment: center;");

        Button backButton = new Button("Back");
        backButton.setOnAction(e -> showHomeScreen());

        if (pizzas.isEmpty()) {
            Label titleLabel = new Label("The menu is empty! Please try again later.");
            titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
            root.getChildren().addAll(titleLabel, backButton);
        } else {
            Label titleLabel = new Label("List of Pizzas");
            titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
            root.getChildren().add(titleLabel);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(10));

            grid.add(new Label("Name"), 0, 0);
            grid.add(new Label("Ingredients"), 1, 0);
            grid.add(new Label("Amount"), 2, 0);

            List<OrderPizza> pizzaList = new ArrayList<>();
            int row = 1;
            for (Pizzaiolo.DetailsPizza pizza : pizzas) {
                grid.add(new Label(pizza.nom()), 0, row);
                grid.add(new Label(pizza.ingredients().stream()
                        .map(Enum::name)
                        .collect(Collectors.joining(", "))), 1, row);
                TextField amountField = new TextField();
                amountField.setPromptText("Enter amount");
                amountField.textProperty().addListener((observable, oldValue, newValue) -> {
                    try {
                        boolean validQty = true;
                        int qty = Integer.parseInt(newValue);
                        if (qty < 1 || qty > 10) {
                            showAlert("Wrong input!", "Amount can not be negative or higher than 10", "Please change the amount.");
                            pizzaList.removeIf(order -> order.getName().equals(pizza.nom()));
                            validQty = false;
                        }
                        if (validQty) {
                            pizzaList.removeIf(order -> order.getName().equals(pizza.nom()));
                            pizzaList.add(new OrderPizza(pizza.nom(), qty));
                        }
                    } catch (NumberFormatException e) {
                        // Ignore invalid input
                    }
                });
                grid.add(amountField, 2, row);
                row++;
            }

            Button placeOrderButton = new Button("Place an Order!");
            placeOrderButton.setOnAction(e -> {
                try {
                    boolean validOrder = true;
                    if (pizzaList.isEmpty()) {
                        showAlert("Wrong Order", "You have not input any valid amount!", "Please input an amount. Products with invalid amount will not be included in the order.");
                        validOrder = false;
                    }
                    if (validOrder) {
                        client.placeOrder(pizzaList);
                        String result = pizzaList.stream()
                                .map(order -> pizzaList.indexOf(order) + 1 + ". " + order.getName() + ": " + order.getQty())
                                .collect(Collectors.joining("\n"));
                        showAlert("Order Placed!", "Order Placed Succesfully:\n" + result, "Redirecting to Home Screen.");
                        showHomeScreen();
                    }
                } catch (MqttException ex) {
                    throw new RuntimeException(ex);
                }
            });

            HBox buttonBox = new HBox(10, backButton, placeOrderButton);
            root.getChildren().addAll(grid, buttonBox);
        }
        primaryStage.setScene(new Scene(root, 1000, 400));
    }

    /**
     * Displays an alert with the specified title, header text, and content text.
     *
     * @param title       the title of the alert
     * @param headerText  the header text of the alert
     * @param contentText the content text of the alert
     */
    private void showAlert(String title, String headerText, String contentText) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(headerText);
        alert.setContentText(contentText);
        alert.showAndWait();
    }

    // ORDER STATUS SCREEN

    /**
     * Displays the status screen where users can check the current status of their active order.
     * The user can refresh the status, confirm delivery, or confirm cancellation.
     */
    private void showStatusScreen() {
        Label statusLabel = new Label(client.orderStatus);
        statusLabel.setStyle("-fx-font-size: 18px;");

        Button backButton = new Button("Back");
        backButton.setStyle("-fx-font-size: 14px;");
        backButton.setOnAction(e -> showHomeScreen());

        Button confirmButton = new Button("Confirm Delivery");
        confirmButton.setStyle("-fx-font-size: 14px;");
        confirmButton.setOnAction(e -> {
            client.confirmDelivery();
            showHomeScreen();
        });

        Button refreshButton = new Button("Refresh Status");
        refreshButton.setStyle("-fx-font-size: 14px;");
        refreshButton.setOnAction(e -> showStatusScreen());

        Button cancellationConfirmButton = new Button("Confirm Cancellation");
        cancellationConfirmButton.setStyle("-fx-font-size: 14px;");
        cancellationConfirmButton.setOnAction(e -> {
            client.confirmCancellation();
            showHomeScreen();
        });

        VBox layout = new VBox(20);
        layout.getChildren().addAll(statusLabel, backButton, refreshButton);
        if (client.orderDelivered) {
            layout.getChildren().add(confirmButton);
        } else if (client.orderCancelled) {
            layout.getChildren().add(cancellationConfirmButton);
        }
        layout.setStyle("-fx-alignment: center; -fx-padding: 20px;");

        Scene scene = new Scene(layout, 1000, 400);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Order Status");
    }
}
