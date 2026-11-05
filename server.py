"""Сервер управления ПК (Windows 11).

Запуск в консоли:       python server.py
Проверка и инфо:        python server.py --init
Скрытый запуск (фон):   pythonw server.py
Автоустановка:          install_autostart.bat (от имени администратора)

При первом запуске создаётся config.json с секретным токеном и списком программ.
Изменения в config.json подхватываются автоматически без перезапуска сервера.
В скрытом режиме ошибки и действия пишутся в server.log рядом с этим файлом.
"""
import sys
from server import (
    PORT,
    TOKEN,
    log,
    check_paths,
    get_local_ips,
    start_network_services,
    Handler,
    PcServer,
)


def main() -> None:
    if "--init" in sys.argv:
        ips = ", ".join(get_local_ips()) or "не определён"
        print(f"  IP-адреса ПК: {ips}")
        print(f"  Порт:         {PORT}")
        print(f"  Токен:        {TOKEN}")
        sys.exit(0)

    check_paths()
    local_ips = ", ".join(get_local_ips()) or "127.0.0.1"
    print("\n" + "=" * 70)
    print("  🖥️  ПУЛЬТ ПК v6.0 — СЕРВЕР ЗАПУЩЕН И ГОТОВ К РАБОТЕ")
    print("=" * 70)
    print(f"  🔑 СЕКРЕТНЫЙ ТОКЕН:  {TOKEN}")
    print(f"  🏠 ЛОКАЛЬНЫЙ WI-FI:   http://{local_ips}:{PORT}")
    print("=" * 70 + "\n")
    log(f"Сервер слушает порт {PORT}. Токен: {TOKEN}")

    start_network_services()

    try:
        server = PcServer(("0.0.0.0", PORT), Handler)
    except OSError as e:
        log(f"[!] Не удалось занять порт {PORT} (возможно, сервер уже запущен): {e}")
        raise SystemExit(1)
    server.serve_forever()


if __name__ == "__main__":
    main()
