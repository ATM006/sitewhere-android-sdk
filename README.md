![SiteWhere](https://s3.amazonaws.com/sitewhere-demo/sitewhere-small.png)

# SiteWhere Android SDK

[![license](https://img.shields.io/badge/license-CPAL--1-blue.svg?style=flat)](https://raw.githubusercontent.com/sitewhere/sitewhere-android-sdk/master/LICENSE.txt)

This software development kit allows Android devices to interact with SiteWhere.
By default, the MQTT protocol is used to create a persistent connection between the
application and a SiteWhere instance. Devices can register with the system, publish events
using the provided API and receive commands which can be used to execute code on the
device.

## Developer Setup

* Install [Java SE SDK 1.8](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html).
* Install [Android Studio](http://developer.android.com/sdk/index.html).
* Download Android SDK 19 (It is possible to use this SDK with a lower SDK version).

## Quickstart

Step 1. Clone this repository.

Step 2. Create a new Project.

Step 3. Add the following to the settings.gradle file:

```groovy
include ':sitewhere-android-sdk'
project(':sitewhere-android-sdk').projectDir = new File('../sitewhere-android-sdk') // <- points to the 'sitewhere-android-sdk' folder inside local repository cloned in Step 1 
```

Step 4. Select "Open Module Settings" and add module "sitewhere-android-sdk" in the Dependencies tab.

# Sample Application

The sample app can be found in the SiteWhereExample folder.  The app demostrates how an Android device can be an IoT gateway and/or client device for SiteWhere.  As an IoT gateway you can register an Android device with SiteWhere and send location and measurement events.  As an IoT client you can register to have events pushed in real-time to an Android device.  Configuring what events get pushed to a specific device is done using server side filters and groovy scripts.  The sample app uses the device's current location and accelerometer.

# Discussion

[Join the discussion](https://discord.gg/sq7sH7B)
