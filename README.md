Pizzeria Client
Overview

The Pizzeria Client is a Java-based application that allows customers to interact with an MQTT-based pizzeria system. It enables users to request a menu, place orders, and track order statuses using an interactive command-line or graphical interface.
Features

    Request and retrieve the menu from the pizzeria.
    Place an order for one or more pizzas.
    Track order status in real-time.
    Receive notifications on order progress, including cancellations and deliveries.
    Graphical User Interface (GUI) for easy interaction.

Prerequisites

Ensure you have the following installed:

    Java 17 or later
    Maven (for building the project)
    An MQTT broker (Mosquitto)

The MQTT broker can be installed and used through Docker with:
docker pull eclipse-mosquitto

Run the docker image with:
docker run -d --name mosquitto -p 1883:1883 -p 9001:9001 eclipse-mosquitto

After mosquitto is running, be sure to modify the following configuration in the container:
/mosquitto/mosquitto.conf

Add the following configurations to allow the server/client to communicate with the broker:

    listener 1883
    allow_anonymous true

Installation

Clone the repository:

git clone https://github.com/Meganmumajesi/pizzeria-client.git
cd pizzeria-client

Build the project using Maven:

    mvn clean package

If the pizzaiolo jar is not recognized, install it so maven can recognize it:

    mvn install:install-file -Dfile=../path/to/pizzaiolo.jar -DgroupId=lets-make-a-pizza -DartifactId=pizzaiolo -Dversion=1.0 -Dpackaging=jar

It is referenced by:

    <dependency>
      <groupId>lets-make-a-pizza</groupId>
      <artifactId>pizzaiolo</artifactId>
      <version>1.0</version>
    </dependency>

The already built pizzeria-client-1.0-SNAPSHOT.jar is included in the /jar directory

The openjfx-21.0.6 sdk is included in the /openjfc-21.0.6_linux-x64_bin-sdk folder. If on another OS, download it from: https://gluonhq.com/products/javafx/ (https://openjfx.io/openjfx-docs/index.html)

Running the Application

Run the client application/launch the GUI with:

    java --module-path /path/to/openjfx/sdk/lib/openjfx-21.0.6_linux-x64_bin-sdk/javafx-sdk-21.0.6/lib --add-modules javafx.controls,javafx.fxml -jar target/pizzeria-client-1.0-SNAPSHOT.jar

Configuration

The application uses an app.properties file for configuration. Ensure it contains the following values:

    broker.url=tcp://localhost:1883
    order.topic=orders/
    bcast.request.menu.topic=bcast/i_am_ungry
    bcast.retrieve.menu.topic=bcast/menu
    qos=0

Usage

The client automatically starts and subscribes to the necessary MQTT topics. You can request a menu and place orders via MQTT messages.
Graphical User Interface (GUI)

Order Format (Custom serialization/deserialization format)

Orders are sent in the format:

pizza_name,quantity~pizza_name,quantity

Example:

margherita,2~pepperoni,1

MQTT Topics

    Menu Request: bcast/i_am_ungry
    Menu Response: bcast/menu
    Order Submission: orders/{order_id}
    Order Status Updates: orders/{order_id}/status
    Order Cancellation: orders/{order_id}/cancelled
    Order Delivery: orders/{order_id}/delivery

Logging

The application logs MQTT interactions using SLF4J. Logs can be found in the console or configured in the /logs directory

Troubleshooting

    Ensure the MQTT broker is running.
    Check the app.properties configuration.
    Verify network connectivity between the client and the broker.
