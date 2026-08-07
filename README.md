# Tailscale Switch

Tailscale Switch is a small Android application that automatically manages your Tailscale VPN activation based on your network connection.

The goal is to ensure your connection is always secure on unknown or mobile networks, while automatically disabling the VPN when you are on trusted Wi-Fi networks.

## Features

- **Automatic Activation**: Connects Tailscale on mobile data and unlisted Wi-Fi networks.
- **Trusted Networks**: Allows you to define a list of Wi-Fi networks (SSIDs) where Tailscale should be disabled (e.g., home, office).
- **Smart Network Loss Management**: Disconnects Tailscale after 1 minute without internet connection to save battery.
- **Automatic Reconnection**: Restarts the VPN as soon as the network returns, unless you are on a trusted Wi-Fi.

## Installation and Usage

1. Install the app and grant the necessary permissions (location is required by Android to read the Wi-Fi name).
2. Add your trusted Wi-Fi networks to the list.
3. Enable the monitoring service.

## License

This project is open source under the Apache License 2.0. See the [LICENSE](LICENSE) file for more details.
