# XVoicePlus (XVoice++)
XVoicePlus is an Xposed module that allows you to use standard SMS apps to send and receive messages through Google Voice. When this module is activated, any outgoing SMS messages will be rerouted through Google Voice, and any messages that are received by the Google Voice app will be displayed in SMS apps.

This module is a fork of [runnirr](https://github.com/runnirr)'s original [XVoicePlus (XVoice+)](https://github.com/runnirr/XVoicePlus) module, which is an Xposed implementation of CyanogenMod's VoicePlus. I renamed this module XVoice++ to highlight the difference, although the naming scheme of XVoicePlus is the same.

## Installation
* Install Google Voice from the [Play Store](https://play.google.com/store/apps/details?id=com.google.android.apps.googlevoice) if you don't have it already.
* Install XVoice++ from the Xposed Installer app. (Or download the latest APK from the [releases page](/releases/latest) or the [Xposed Repo](http://repo.xposed.info/module/io.behindthemath.xvoiceplus) and side-load it).
* Activate XVoice++ in Xposed.
* Open the XVoice++ app and enabled it, and select the Google Voice account you would like to use.
* Disable text notifications in the Google Voice app settings to avoid double notifications.
* Soft-reboot to enable the Xposed module.
* You will get a notification to sign in to authorize the app to access your account. Tap the notification, then tap allow. For some reason the notification is not dismissed automatically; you can dismiss it manually.

## How it works
### Sending
The XVoicePlusService runs on startup and listens for an OUTGOING_SMS so that it knows when a message is being sent. Cyanogenmod made this a system-wide intent (NEW_OUTGOING_SMS), but it is not available on all other ROMs. Instead, the module hooks SmsManager to catch the outgoing message and send the OUTGOING_SMS intent instead.

Once XVoicePlusService receives the intent, it sends the message using HTTP calls to the legacy Google Voice website.

### Receiving
##### Legacy Google Voice (v0.4.7.10 and lower)
Receiving message is done by hooking into the Google Voice app's PushNotificationReceiver. This means that we know of the message as soon as the Google Voice app does. Conveniently, this receiver works even when notifications in Google Voice are turned off, so you don't need to briefly see the Google Voice notification, have it disappear, and then see the SMS notification.

Once we have an incoming Google Voice message, we broadcast an INCOMING_VOICE intent to the XVoicePlusService. The service then handles the message and broadcasts a system wide RECEIVED_SMS intent that is used by all SMS applications.

##### Google Voice v5.0+
Starting with v5.0, the Google Voice app was completely rewritten, and now uses Google Cloud Messaging for push notifications. The module hooks the GCMListenerService to catch the message as it comes in, and broadcasts an INCOMING_VOICE intent to the XVoicePlusService.

## Known Issues
* Emojis and accented characters may cause issues.
* There is no support for MMS.
* There is no way to send SMS via your carrier. Any app that uses SmsManager to send texts will have its messages redirected via Google Voice.
* Occasionally, messages may show up with the wrong timestamp, or there may be duplicate messages.
* When I had the default SMS app is set to the AOSP Messaging app (com.android.mms, not to be confused with Google Messenger), the module would not work for outgoing messages. On one device, incoming messages did not work either. I was using version 5.1.1-720affab4e. I'm still not sure what causes it.
* On one of my test devices, I sometimes experienced a crash of the system process on boot. This did not seem to affect the functionality of the module.

## To do list
* Blacklist / Whitelist

## Support
[XDA Thread](https://forum.xda-developers.com/xposed/modules/app-xvoice-google-voice-sms-apps-t3556861)

## Acknowledgments
* [Koush](https://github.com/koush) for writing the original VoicePlus
* CyanogenMod team for adding it in to CM 10.2 - 11
* [runnirr](https://github.com/runnirr) for creating the original XVoice+
* [B2OJustin](https://github.com/Justin42) for taking over development of XVoice+
* [iHelp101](https://github.com/iHelp101) for updating XVoice+ to work with CM12

## License
    Copyright © 2017 Behind The Math

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.