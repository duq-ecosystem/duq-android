#!/usr/bin/env bash
# adb-wifi.sh — перевод dev-телефона на ADB по Wi-Fi, чтобы дебажить без USB.
#
# Зачем: боевой телефон трогать нельзя, поэтому dev-устройство (старый телефон)
# держим на беспроводном ADB. Один раз воткнул в USB → запустил скрипт →
# дальше кабель не нужен (до ребута телефона; после ребута — повторить с USB,
# либо использовать Android 11+ Wireless debugging с пейрингом, см. ниже).
#
# Использование:
#   ./scripts/adb-wifi.sh            # автоопределение: телефон по USB → перевод на Wi-Fi
#   ./scripts/adb-wifi.sh <ip>       # просто переподключиться к уже включённому tcpip
#   ./scripts/adb-wifi.sh reconnect  # переподключиться к последнему сохранённому ip
#
set -euo pipefail

PORT=5555
STATE_FILE="${TMPDIR:-/tmp}/duq-adb-wifi-ip"

connect() {
  local ip="$1"
  echo "→ adb connect ${ip}:${PORT}"
  adb connect "${ip}:${PORT}"
  echo "$ip" > "$STATE_FILE"
  echo "✓ Сохранён ip: $ip (повтор: ./scripts/adb-wifi.sh reconnect)"
  adb -s "${ip}:${PORT}" shell getprop ro.product.model
}

case "${1:-}" in
  reconnect)
    [ -f "$STATE_FILE" ] || { echo "Нет сохранённого ip. Запусти с USB сначала."; exit 1; }
    connect "$(cat "$STATE_FILE")"
    ;;
  "" )
    # Авто: ищем устройство по USB (transport usb), включаем tcpip, читаем wlan0 ip.
    usb_serial="$(adb devices -l | awk '/usb:/{print $1; exit}')"
    [ -n "$usb_serial" ] || { echo "Телефон по USB не найден. Воткни кабель + включи USB-debugging."; exit 1; }
    echo "USB-устройство: $usb_serial"
    ip="$(adb -s "$usb_serial" shell ip -f inet addr show wlan0 2>/dev/null \
          | awk '/inet /{print $2}' | cut -d/ -f1 | head -n1)"
    [ -n "$ip" ] || { echo "Не удалось узнать Wi-Fi IP телефона (wlan0 выключен?)."; exit 1; }
    echo "Wi-Fi IP телефона: $ip"
    echo "→ adb -s $usb_serial tcpip $PORT"
    adb -s "$usb_serial" tcpip "$PORT"
    sleep 2
    connect "$ip"
    echo "Кабель можно отключать."
    ;;
  * )
    connect "$1"
    ;;
esac
