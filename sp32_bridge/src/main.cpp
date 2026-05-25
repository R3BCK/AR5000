#include <WiFi.h>
#include <WiFiClient.h>
#include <WiFiServer.h>
const char* SSID = "MGTS_GPON_2787"; const char* PASS = "EKGPQUVE";
const uint16_t PORT = 2323;
HardwareSerial ar5000(2);
WiFiServer srv(PORT); WiFiClient cli;

void setup() {
    Serial.begin(115200);
    ar5000.begin(9600, SERIAL_8N2, 16, 17);
    WiFi.begin(SSID, PASS); while(WiFi.status()!=WL_CONNECTED) delay(500);
    srv.begin();
}

void loop() {
    if(!cli.connected()) { cli = srv.available(); if(cli && cli.connected()) { cli.println("AR5000-BRIDGE:READY"); } }
    if(cli.connected()) {
        while(cli.available()) ar5000.write(cli.read());
        while(ar5000.available()) cli.write(ar5000.read());
    }
}