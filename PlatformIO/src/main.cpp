// File: src/main.cpp
// AR5000 RS232 to WiFi TCP Bridge
// All comments use ASCII-only characters

#include <WiFi.h>
#include <WiFiClient.h>
#include <WiFiServer.h>

// Network configuration
const char* WIFI_SSID = "MGTS_GPON_2787";
const char* WIFI_PASS = "EKGPQUVE";
const uint16_t TCP_PORT = 2323;

// UART2 configuration for AR5000
// 9600 baud, 8 data bits, 2 stop bits, no parity
#define UART_RX_PIN 16
#define UART_TX_PIN 17
#define UART_BAUD 9600

HardwareSerial ar5000Serial(2);
WiFiServer tcpServer(TCP_PORT);
WiFiClient tcpClient;

bool clientConnected = false;
uint32_t lastActivity = 0;
const uint32_t CLIENT_TIMEOUT_MS = 30000;

void setup() {
    Serial.begin(115200);
    Serial.println("[BRIDGE] ESP32 AR5000 Bridge starting...");

    // Initialize UART2
    ar5000Serial.begin(UART_BAUD, SERIAL_8N2, UART_RX_PIN, UART_TX_PIN);
    Serial.println("[BRIDGE] UART2 initialized: 9600 8N2");

    // Connect to WiFi
    WiFi.begin(WIFI_SSID, WIFI_PASS);
    Serial.print("[BRIDGE] Connecting to WiFi");
    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 20) {
        delay(500);
        Serial.print(".");
        attempts++;
    }

    if (WiFi.status() == WL_CONNECTED) {
        Serial.println("\n[BRIDGE] WiFi connected");
        Serial.printf("[BRIDGE] IP: %s\n", WiFi.localIP().toString().c_str());
    } else {
        Serial.println("\n[BRIDGE] WiFi connection failed");
    }

    // Start TCP server
    tcpServer.begin();
    Serial.printf("[BRIDGE] TCP server started on port %d\n", TCP_PORT);

    lastActivity = millis();
}

void loop() {
    // WiFi reconnection check
    if (WiFi.status() != WL_CONNECTED) {
        WiFi.begin(WIFI_SSID, WIFI_PASS);
        delay(2000);
    }

    // Handle new TCP connection
    if (!clientConnected) {
        if (!tcpClient.connected()) {
            tcpClient = tcpServer.available();
            if (tcpClient && tcpClient.connected()) {
                clientConnected = true;
                lastActivity = millis();
                tcpClient.println("AR5000-BRIDGE:READY");
                while (ar5000Serial.available()) ar5000Serial.read();
                Serial.println("[BRIDGE] Client connected");
            }
        }
    } else {
        // Handle existing client
        if (!tcpClient.connected()) {
            clientConnected = false;
            tcpClient.stop();
            Serial.println("[BRIDGE] Client disconnected");
        } else {
            // Forward TCP -> UART
            while (tcpClient.available()) {
                uint8_t b = tcpClient.read();
                ar5000Serial.write(b);
                lastActivity = millis();
            }
            // Forward UART -> TCP
            while (ar5000Serial.available()) {
                uint8_t b = ar5000Serial.read();
                tcpClient.write(b);
                lastActivity = millis();
            }
        }
    }

    // Client timeout
    if (clientConnected && millis() - lastActivity > CLIENT_TIMEOUT_MS) {
        tcpClient.stop();
        clientConnected = false;
        Serial.println("[BRIDGE] Client timeout");
    }

    delay(2);
}