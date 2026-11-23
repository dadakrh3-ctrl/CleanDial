# Clean Dialer

A dark Android phone and SMS app built with Kotlin and Jetpack Compose.

## Features in this build

- Phone keypad and recent-call history.
- Top switch between Phone and Messages.
- SMS inbox, conversation view, and new-message composer.
- SMS sends through the device's cellular SMS service. The recipient does not need Clean Dialer; they can use Google Messages or any ordinary SMS app.
- Requests to become the device's default Phone app (dialer) and default SMS app.
- Basic in-call screen wired to Android Telecom for answering and ending calls.
- Incoming SMS delivery receiver and SMS provider storage.

## Build the APK

1. Open this folder in Android Studio, or push the complete folder to a GitHub repository.
2. On GitHub, open **Actions** and run **Build APK** (or push a commit to trigger it).
3. Download the `CleanDialer-apk` artifact from the completed workflow.
4. Install `app-debug.apk` on your Android phone.

## Set as default apps

Open Clean Dialer and use **Make default phone app** and **Make default SMS app**. Android displays its own confirmation screen; accept it to grant the role. You can change these choices later in Android Settings > Apps > Default apps.

The phone role requires a SIM/cellular-capable device and Android's Telecom framework. SMS requires an active SMS-capable SIM and carrier service. Standard carrier SMS charges may apply.

## Important current limitations

- SMS is implemented; MMS delivery/attachment parsing is not yet implemented, even though the app declares the Android MMS delivery receiver required for default-SMS-app eligibility.
- The in-call screen is intentionally basic. It provides answer/end controls but does not yet include a full production call UI (hold, mute, speaker, Bluetooth routing, conference calls, call waiting, contact lookup, etc.).
- The SMS inbox reads the system SMS provider when Clean Dialer is the default SMS app (or the user has granted the relevant SMS permission). Android controls role grants and may vary by device/manufacturer.
- Emergency calls and some carrier/system call flows may still be handled by Android's system dialer.
