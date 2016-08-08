#include <SPI.h>
#include "Adafruit_BLE_UART.h"
#include <Servo.h> 
#include <QueueList.h>

// Bluetooth LE
#define ADAFRUITBLE_REQ 10
#define ADAFRUITBLE_RDY 2
#define ADAFRUITBLE_RST 9

Adafruit_BLE_UART BTLEserial = Adafruit_BLE_UART(ADAFRUITBLE_REQ, ADAFRUITBLE_RDY, ADAFRUITBLE_RST);

QueueList <String> BTQueue;

enum ViewDirection
{
    SERVO_UP,
    SERVO_DOWN,
    SERVO_LEFT,
    SERVO_RIGHT
};

int LED_PIN = 5;
int SERVO_LEFTRIGHT = 6;
int SERVO_UPDOWN = 7;

Servo cameraViewLeftRight;    // Move camera Left <-> Right 0 ~ 180(center 90)
Servo cameraViewUpDown;       // Move camera Up <-> Down 80-180(center 120)

int currentViewUpDownAngle = -1;
int currentViewLeftRightAngle = -1;


void ServoCenter()
{
    currentViewLeftRightAngle = 90;
    cameraViewLeftRight.write(currentViewLeftRightAngle);
    currentViewUpDownAngle = 20;
    cameraViewUpDown.write(currentViewUpDownAngle);
}

void ServoUpDown(int UpDown)
{
    if(UpDown == SERVO_DOWN)
    {
        currentViewUpDownAngle = currentViewUpDownAngle - 5;

        if(currentViewUpDownAngle < 0)
        {
            currentViewUpDownAngle = 0;
        }
    }
    else if(UpDown == SERVO_UP)
    {
        currentViewUpDownAngle = currentViewUpDownAngle + 5;

        if(currentViewUpDownAngle > 120)
        {
            currentViewUpDownAngle = 120;
        }

    }
    else
    {
        return;
    }

    cameraViewUpDown.write(currentViewUpDownAngle);
}

void ServoLeftRight(int LeftRight)
{
    if(LeftRight == SERVO_LEFT)
    {
        currentViewLeftRightAngle = currentViewLeftRightAngle - 5;

        if(currentViewLeftRightAngle > 180)
        {
            currentViewLeftRightAngle = 180;
        }

    }
    else if(LeftRight == SERVO_RIGHT)
    {
        currentViewLeftRightAngle = currentViewLeftRightAngle + 5;

        if(currentViewLeftRightAngle < 0)
        {
            currentViewLeftRightAngle = 0;
        }

    }
    else
    {
        return;
    }
    
    cameraViewLeftRight.write(currentViewLeftRightAngle);
}

void setup() {
  Serial.begin(115200);

  pinMode(LED_PIN, OUTPUT);
  ledControl(false);
  
  // BLE
  BTLEserial.setDeviceName("BrainyC"); /* 7 characters max! */
  BTLEserial.begin();
  
  cameraViewLeftRight.attach(SERVO_LEFTRIGHT);
  cameraViewUpDown.attach(SERVO_UPDOWN);
  ServoCenter();
}

void loop() {
  String rcvDataStr = readBT();
  
  if(rcvDataStr.length() > 0)
  {
    if(rcvDataStr.equals("c")) {
      ServoCenter();
    }
    else if(rcvDataStr.equals("u")) {
      ServoUpDown(SERVO_UP);
    }
    else if(rcvDataStr.equals("d")) {
      ServoUpDown(SERVO_DOWN);
    }
    else if(rcvDataStr.equals("l")) {
      ServoLeftRight(SERVO_LEFT);
    }
    else if(rcvDataStr.equals("r")) {
      ServoLeftRight(SERVO_RIGHT);
    }
    else if(rcvDataStr.equals("ledOn")) {
      ledControl(true);
    }
    else if(rcvDataStr.equals("ledOff")) {
      ledControl(false);
    }
  }

}

String readBT()
{
  String rcvDataStr = "";
  
  BTLEserial.pollACI();
  aci_evt_opcode_t status = BTLEserial.getState();
  
  if (status == ACI_EVT_CONNECTED) 
  {
    if(BTLEserial.available() > 0)
    {
      while (BTLEserial.available()) 
      {
        char c = BTLEserial.read();
        rcvDataStr.concat(c);
      }
      Serial.println(rcvDataStr);
    }
  }
  else
  {
    return "";
  }
  return rcvDataStr;
}


void writeBT(String str, String prefix)
{
  String sendStr = prefix + ";" + str;
  
  BTQueue.push(sendStr);
}

void sendBT()
{
  if(BTQueue.isEmpty() != true)
  {
    uint8_t sendbuffer[20];
    String sendStr = BTQueue.pop ();
    sendStr.getBytes(sendbuffer, 20);
    char sendbuffersize = min(20, sendStr.length());
  
    Serial.print("BTLEserial.getState(): "); Serial.println(BTLEserial.getState());
    
    // write the data
    if(BTLEserial.getState() == ACI_EVT_CONNECTED)
    {
      Serial.print(F("\n* Sending -> \"")); Serial.print((char *)sendbuffer); Serial.println("\"");
      BTLEserial.write(sendbuffer, sendbuffersize);
    }
    delay(100);
  }
}

void ledControl(boolean onOff)
{
  if(onOff == true)
  {
    analogWrite(LED_PIN, 255);
  }
  else
  {
    analogWrite(LED_PIN, 0);
  }
  
}

