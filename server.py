"""Сервер управления ПК (Windows 11). Только стандартная библиотека Python 3 + WinAPI.

Запуск в консоли:       python server.py
Проверка и инфо:        python server.py --init
Скрытый запуск (фон):   pythonw server.py
Автоустановка:          install_autostart.bat (от имени администратора)

При первом запуске создаётся config.json с секретным токеном и списком программ.
Изменения в config.json подхватываются автоматически без перезапуска сервера.
В скрытом режиме ошибки и действия пишутся в server.log рядом с этим файлом.
"""
import ctypes
import hmac
import json
import re
from typing import Optional, List, Dict, Any
import os
import secrets
import shutil
import socket
import subprocess
import sys
import threading
import time
import traceback
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, quote_plus, urlparse

PORT = 8765
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CFG_PATH = os.path.join(BASE_DIR, "config.json")
LOG_PATH = os.path.join(BASE_DIR, "server.log")
UPDATES_DIR = os.path.join(BASE_DIR, "updates")

# Папка для сохранения фото/видео со смартфона
# Если диск D: существует на ПК — автоматически сохраняем в D:\Photos, либо по пути из config.json
def _get_media_dir():
    try:
        if os.path.exists(CFG_PATH):
            with open(CFG_PATH, "r", encoding="utf-8") as f:
                cfg = json.load(f)
                custom = cfg.get("photos_dir")
                if custom:
                    return custom
    except Exception:
        pass
    if os.path.exists("D:\\"):
        return r"D:\Photos"
    return os.path.join(BASE_DIR, "photos")

MEDIA_DIR = _get_media_dir()
try:
    os.makedirs(MEDIA_DIR, exist_ok=True)
except Exception:
    pass
LOG_MAX_BYTES = 200_000
IS_WINDOWS = os.name == "nt"

# Флаги процессов Windows
CREATE_NO_WINDOW = 0x08000000 if IS_WINDOWS else 0
DETACHED_FLAGS = 0x00000208 if IS_WINDOWS else 0  # DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP

BRAVE = r"%ProgramFiles%\BraveSoftware\Brave-Browser\Application\brave.exe"
BROWSER_PROCESS = "brave.exe"

DEFAULT_APPS = [
    {
        "id": "brave",
        "title": "Brave",
        "launch": [BRAVE],
        "process": "brave.exe",
        "force": False,
    },
    {
        "id": "youtube",
        "title": "YouTube в Brave",
        "launch": [BRAVE, "https://www.youtube.com"],
        "process": None,
        "force": False,
    },
    {
        "id": "discord",
        "title": "Discord",
        "launch": [r"%LOCALAPPDATA%\Discord\Update.exe", "--processStart", "Discord.exe"],
        "process": "Discord.exe",
        "force": True,
    },
    {
        "id": "yandex-music",
        "title": "Яндекс Музыка",
        "launch": [r"%LOCALAPPDATA%\Programs\YandexMusic\Яндекс Музыка.exe"],
        "process": "Яндекс Музыка.exe",
        "force": True,
    },
    {
        "id": "premiere",
        "title": "Premiere Pro",
        "launch": [r"%ProgramFiles%\Adobe\Adobe Premiere Pro 2026\Adobe Premiere Pro.exe"],
        "process": "Adobe Premiere Pro.exe",
        "force": False,
    },
]

_cfg_lock = threading.Lock()
_cfg_mtime: float = -1.0
CFG: dict = {}
TOKEN: str = ""
APPS: dict = {}
APP_LIST: list = []
_shutdown_deadline: float = 0.0

# Кеш последнего JPEG-кадра экрана (дедупликация частых запросов)
_screen_lock = threading.Lock()
_last_jpeg: bytes = b""
_last_jpeg_ts: float = 0.0
_last_jpeg_key: tuple = (0, 0, False)
SCREEN_CACHE_TTL = 0.003  # до 120 кадров/сек (поддержка 90 FPS без задержек)


def log(msg: str, to_file: bool = True) -> None:
    """Вывод в консоль и в server.log. Не падает при запуске через pythonw.exe."""
    line = f"{time.strftime('%Y-%m-%d %H:%M:%S')} {msg}"
    try:
        print(line, flush=True)
    except Exception:
        pass
    if not to_file:
        return
    try:
        if os.path.exists(LOG_PATH) and os.path.getsize(LOG_PATH) > LOG_MAX_BYTES:
            os.replace(LOG_PATH, LOG_PATH + ".old")
        with open(LOG_PATH, "a", encoding="utf-8") as f:
            f.write(line + "\n")
    except Exception:
        pass


def save_config(cfg: dict) -> None:
    with open(CFG_PATH, "w", encoding="utf-8") as f:
        json.dump(cfg, f, ensure_ascii=False, indent=2)


def reload_config_if_needed() -> None:
    """Подхватывает изменения config.json на лету без перезапуска сервера."""
    global _cfg_mtime, CFG, TOKEN, APPS, APP_LIST
    with _cfg_lock:
        try:
            mtime = os.path.getmtime(CFG_PATH) if os.path.exists(CFG_PATH) else 0.0
        except OSError:
            mtime = 0.0

        if _cfg_mtime == mtime and CFG:
            return

        cfg = {}
        if os.path.exists(CFG_PATH):
            try:
                with open(CFG_PATH, "r", encoding="utf-8") as f:
                    cfg = json.load(f)
                if not isinstance(cfg, dict):
                    raise ValueError("в файле должен быть JSON-объект")
            except Exception as e:
                log(f"[!] Ошибка чтения config.json: {e}")
                _cfg_mtime = mtime
                if CFG:
                    return
                raise SystemExit(
                    f"config.json повреждён ({e}). Исправь его или удали, чтобы создать заново."
                )

        changed = False
        if "token" not in cfg:
            cfg["token"] = secrets.token_urlsafe(24)
            changed = True
        if "apps" not in cfg:
            cfg["apps"] = DEFAULT_APPS
            changed = True
        if changed:
            save_config(cfg)
            try:
                mtime = os.path.getmtime(CFG_PATH)
            except OSError:
                pass

        apps_ok = []
        for a in cfg["apps"]:
            if isinstance(a, dict) and a.get("id") and a.get("title") and a.get("launch"):
                apps_ok.append(a)
            else:
                log(f"[!] Пропускаю некорректную запись в apps: {a!r}")

        CFG = cfg
        TOKEN = str(cfg["token"])
        APP_LIST = apps_ok
        APPS = {a["id"]: a for a in apps_ok}
        _cfg_mtime = mtime


reload_config_if_needed()

# Команды управления питанием и сессией
POWER = {
    "/shutdown": (["shutdown", "/s", "/t", "3"], 3),
    "/shutdown_15m": (["shutdown", "/s", "/t", "900"], 900),
    "/shutdown_30m": (["shutdown", "/s", "/t", "1800"], 1800),
    "/shutdown_60m": (["shutdown", "/s", "/t", "3600"], 3600),
    "/shutdown_120m": (["shutdown", "/s", "/t", "7200"], 7200),
    "/restart": (["shutdown", "/r", "/t", "3"], 3),
    "/cancel": (["shutdown", "/a"], 0),
    "/lock": (["rundll32.exe", "user32.dll,LockWorkStation"], None),
}

# Одиночные клавиши (код VK_*, количество нажатий)
MEDIA_KEYS = {
    "vol_up": (0xAF, 2),          # VK_VOLUME_UP (~4% за вызов)
    "vol_down": (0xAE, 2),        # VK_VOLUME_DOWN (~4% за вызов)
    "vol_up_10": (0xAF, 5),       # VK_VOLUME_UP (~10% за вызов)
    "vol_down_10": (0xAE, 5),     # VK_VOLUME_DOWN (~10% за вызов)
    "vol_mute": (0xAD, 1),        # VK_VOLUME_MUTE
    "play_pause": (0xB3, 1),      # VK_MEDIA_PLAY_PAUSE (системная)
    "next": (0xB0, 1),            # VK_MEDIA_NEXT_TRACK
    "prev": (0xB1, 1),            # VK_MEDIA_PREV_TRACK
    "yt_play_k": (0x4B, 1),       # Клавиша 'K' (пауза/пуск конкретно в плеере YouTube)
    "yt_back": (0x25, 1),         # VK_LEFT (-5 сек в YouTube/плеере)
    "yt_forward": (0x27, 1),      # VK_RIGHT (+5 сек в YouTube/плеере)
    "yt_back_10": (0x4A, 1),      # Клавиша 'J' (-10 сек в YouTube)
    "yt_forward_10": (0x4C, 1),   # Клавиша 'L' (+10 сек в YouTube)
    "yt_fullscreen": (0x46, 1),   # Клавиша 'F' (полный экран в YouTube/плеере)
    "yt_subtitles": (0x43, 1),    # Клавиша 'C' (субтитры в YouTube)
    "yt_mute": (0x4D, 1),         # Клавиша 'M' (звук в плеере YouTube)
    "yt_theater": (0x54, 1),      # Клавиша 'T' (режим кинотеатра в YouTube)
    "yt_mini": (0x49, 1),         # Клавиша 'I' (мини-плеер в YouTube)
    "yt_up": (0x26, 1),           # VK_UP (предыдущий Shorts / прокрутка вверх)
    "yt_down": (0x28, 1),         # VK_DOWN (следующий Shorts / прокрутка вниз)
    "key_enter": (0x0D, 1),       # Enter
    "key_backspace": (0x08, 1),   # Backspace
    "key_esc": (0x1B, 1),         # Escape
    "key_space": (0x20, 1),       # Пробел
}

# Сочетания клавиш (список кодов VK_*, зажимаемых одновременно)
COMBO_KEYS = {
    "close_tab": [0x11, 0x57],         # Ctrl + W (закрыть текущую вкладку браузера)
    "reopen_tab": [0x11, 0x10, 0x54],  # Ctrl + Shift + T (вернуть закрытую вкладку)
    "next_tab": [0x11, 0x09],          # Ctrl + Tab (следующая вкладка)
    "win_d": [0x5B, 0x44],             # Win + D (свернуть/развернуть все окна)
    "alt_tab": [0x12, 0x09],           # Alt + Tab (переключить активное окно)
    "alt_f4": [0x12, 0x73],            # Alt + F4 (закрыть текущее окно)
    "audio_output": [0x5B, 0x11, 0x56],  # Win + Ctrl + V (меню выбора аудиовыхода в Windows 11)
    "yt_speed_up": [0x10, 0xBE],         # Shift + . (> ускорить видео)
    "yt_speed_down": [0x10, 0xBC],       # Shift + , (< замедлить видео)
}

BROWSER_ACTIONS = {
    "yt_play_k",
    "yt_back",
    "yt_forward",
    "yt_back_10",
    "yt_forward_10",
    "yt_fullscreen",
    "yt_subtitles",
    "yt_mute",
    "yt_theater",
    "yt_mini",
    "yt_speed_up",
    "yt_speed_down",
    "yt_up",
    "yt_down",
    "close_tab",
    "reopen_tab",
    "next_tab",
}

_proc_lock = threading.Lock()
_proc_cache: set = set()
_proc_cache_ts: float = 0.0
PROCESS_CACHE_TTL = 1.5

if IS_WINDOWS:
    from ctypes import wintypes

    _user32 = ctypes.WinDLL("user32", use_last_error=True)
    _kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
    _gdi32 = ctypes.WinDLL("gdi32", use_last_error=True)
    _gdiplus = ctypes.WinDLL("gdiplus", use_last_error=True)
    _ole32 = ctypes.WinDLL("ole32", use_last_error=True)
    _powrprof = ctypes.WinDLL("powrprof", use_last_error=True)

    try:
        _user32.SetProcessDPIAware()
        ctypes.windll.winmm.timeBeginPeriod(1)
    except Exception:
        pass

    ULONG_PTR = wintypes.WPARAM

    class MOUSEINPUT(ctypes.Structure):
        _fields_ = [
            ("dx", wintypes.LONG),
            ("dy", wintypes.LONG),
            ("mouseData", wintypes.DWORD),
            ("dwFlags", wintypes.DWORD),
            ("time", wintypes.DWORD),
            ("dwExtraInfo", ULONG_PTR),
        ]

    class KEYBDINPUT(ctypes.Structure):
        _fields_ = [
            ("wVk", wintypes.WORD),
            ("wScan", wintypes.WORD),
            ("dwFlags", wintypes.DWORD),
            ("time", wintypes.DWORD),
            ("dwExtraInfo", ULONG_PTR),
        ]

    class HARDWAREINPUT(ctypes.Structure):
        _fields_ = [
            ("uMsg", wintypes.DWORD),
            ("wParamL", wintypes.WORD),
            ("wParamH", wintypes.WORD),
        ]

    class _INPUT_UNION(ctypes.Union):
        _fields_ = [
            ("mi", MOUSEINPUT),
            ("ki", KEYBDINPUT),
            ("hi", HARDWAREINPUT),
        ]

    class INPUT(ctypes.Structure):
        _fields_ = [
            ("type", wintypes.DWORD),
            ("u", _INPUT_UNION),
        ]

    class FILETIME(ctypes.Structure):
        _fields_ = [
            ("dwLowDateTime", wintypes.DWORD),
            ("dwHighDateTime", wintypes.DWORD),
        ]

    try:
        _kernel32.GetTickCount64.restype = ctypes.c_ulonglong
    except Exception:
        pass

    class MEMORYSTATUSEX(ctypes.Structure):
        _fields_ = [
            ("dwLength", wintypes.DWORD),
            ("dwMemoryLoad", wintypes.DWORD),
            ("ullTotalPhys", ctypes.c_ulonglong),
            ("ullAvailPhys", ctypes.c_ulonglong),
            ("ullTotalPageFile", ctypes.c_ulonglong),
            ("ullAvailPageFile", ctypes.c_ulonglong),
            ("ullTotalVirtual", ctypes.c_ulonglong),
            ("ullAvailVirtual", ctypes.c_ulonglong),
            ("ullAvailExtendedVirtual", ctypes.c_ulonglong),
        ]

    class PROCESSENTRY32W(ctypes.Structure):
        _fields_ = [
            ("dwSize", wintypes.DWORD),
            ("cntUsage", wintypes.DWORD),
            ("th32ProcessID", wintypes.DWORD),
            ("th32DefaultHeapID", ULONG_PTR),
            ("th32ModuleID", wintypes.DWORD),
            ("cntThreads", wintypes.DWORD),
            ("th32ParentProcessID", wintypes.DWORD),
            ("pcPriClassBase", wintypes.LONG),
            ("dwFlags", wintypes.DWORD),
            ("szExeFile", wintypes.WCHAR * 260),
        ]

    class GUID(ctypes.Structure):
        _fields_ = [
            ("Data1", wintypes.DWORD),
            ("Data2", wintypes.WORD),
            ("Data3", wintypes.WORD),
            ("Data4", ctypes.c_ubyte * 8),
        ]

    class GdiplusStartupInput(ctypes.Structure):
        _fields_ = [
            ("GdiplusVersion", wintypes.UINT),
            ("DebugEventCallback", ctypes.c_void_p),
            ("SuppressBackgroundThread", wintypes.BOOL),
            ("SuppressExternalCodecs", wintypes.BOOL),
        ]

    class EncoderParameter(ctypes.Structure):
        _fields_ = [
            ("Guid", GUID),
            ("NumberOfValues", wintypes.ULONG),
            ("Type", wintypes.ULONG),
            ("Value", ctypes.c_void_p),
        ]

    class EncoderParameters(ctypes.Structure):
        _fields_ = [
            ("Count", wintypes.UINT),
            ("Parameter", EncoderParameter * 1),
        ]

    INPUT_MOUSE = 0
    INPUT_KEYBOARD = 1
    KEYEVENTF_KEYUP = 0x0002
    KEYEVENTF_UNICODE = 0x0004
    MOUSEEVENTF_MOVE = 0x0001
    MOUSEEVENTF_LEFTDOWN = 0x0002
    MOUSEEVENTF_LEFTUP = 0x0004
    MOUSEEVENTF_RIGHTDOWN = 0x0008
    MOUSEEVENTF_RIGHTUP = 0x0010
    MOUSEEVENTF_MIDDLEDOWN = 0x0020
    MOUSEEVENTF_MIDDLEUP = 0x0040
    MOUSEEVENTF_WHEEL = 0x0800
    MOUSEEVENTF_VIRTUALDESK = 0x4000
    MOUSEEVENTF_ABSOLUTE = 0x8000
    CF_UNICODETEXT = 13
    TH32CS_SNAPPROCESS = 0x00000002
    WM_CLOSE = 0x0010

    CLSID_JPEG = GUID(
        0x557CF401,
        0x1A04,
        0x11D3,
        (ctypes.c_ubyte * 8)(0x9A, 0x73, 0x00, 0x00, 0xF8, 0x1E, 0xF3, 0x2E),
    )
    GUID_QUALITY = GUID(
        0x1D5BE4B5,
        0xFA4A,
        0x452D,
        (ctypes.c_ubyte * 8)(0x9C, 0xDD, 0x5D, 0xB3, 0x51, 0x05, 0xE7, 0xEB),
    )

    _user32.GetForegroundWindow.restype = wintypes.HWND
    _user32.GetWindowThreadProcessId.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.DWORD)]
    _user32.GetWindowThreadProcessId.restype = wintypes.DWORD
    _user32.IsWindowVisible.argtypes = [wintypes.HWND]
    _user32.GetWindowTextLengthW.argtypes = [wintypes.HWND]
    _user32.GetWindowTextW.argtypes = [wintypes.HWND, wintypes.LPWSTR, ctypes.c_int]
    _user32.IsIconic.argtypes = [wintypes.HWND]
    _user32.IsZoomed.argtypes = [wintypes.HWND]
    _user32.ShowWindow.argtypes = [wintypes.HWND, ctypes.c_int]
    _user32.SetForegroundWindow.argtypes = [wintypes.HWND]
    _user32.PostMessageW.argtypes = [wintypes.HWND, wintypes.UINT, wintypes.WPARAM, wintypes.LPARAM]
    _user32.SendInput.argtypes = [wintypes.UINT, ctypes.POINTER(INPUT), ctypes.c_int]
    _user32.SendInput.restype = wintypes.UINT
    _user32.GetSystemMetrics.argtypes = [ctypes.c_int]
    _user32.GetSystemMetrics.restype = ctypes.c_int
    _user32.GetDC.argtypes = [wintypes.HWND]
    _user32.GetDC.restype = wintypes.HDC
    _user32.ReleaseDC.argtypes = [wintypes.HWND, wintypes.HDC]
    _user32.GetCursorPos.argtypes = [ctypes.POINTER(wintypes.POINT)]
    _user32.OpenClipboard.argtypes = [wintypes.HWND]
    _user32.OpenClipboard.restype = wintypes.BOOL
    _user32.CloseClipboard.argtypes = []
    _user32.EmptyClipboard.argtypes = []
    _user32.GetClipboardData.argtypes = [wintypes.UINT]
    _user32.GetClipboardData.restype = wintypes.HANDLE
    _user32.SetClipboardData.argtypes = [wintypes.UINT, wintypes.HANDLE]
    _user32.SetClipboardData.restype = wintypes.HANDLE

    _gdi32.CreateCompatibleDC.argtypes = [wintypes.HDC]
    _gdi32.CreateCompatibleDC.restype = wintypes.HDC
    _gdi32.CreateCompatibleBitmap.argtypes = [wintypes.HDC, ctypes.c_int, ctypes.c_int]
    _gdi32.CreateCompatibleBitmap.restype = wintypes.HBITMAP
    _gdi32.SelectObject.argtypes = [wintypes.HDC, wintypes.HGDIOBJ]
    _gdi32.SelectObject.restype = wintypes.HGDIOBJ
    _gdi32.SetStretchBltMode.argtypes = [wintypes.HDC, ctypes.c_int]
    _gdi32.StretchBlt.argtypes = [
        wintypes.HDC,
        ctypes.c_int,
        ctypes.c_int,
        ctypes.c_int,
        ctypes.c_int,
        wintypes.HDC,
        ctypes.c_int,
        ctypes.c_int,
        ctypes.c_int,
        ctypes.c_int,
        wintypes.DWORD,
    ]
    _gdi32.CreatePen.argtypes = [ctypes.c_int, ctypes.c_int, wintypes.DWORD]
    _gdi32.CreatePen.restype = wintypes.HPEN
    _gdi32.CreateSolidBrush.argtypes = [wintypes.DWORD]
    _gdi32.CreateSolidBrush.restype = wintypes.HBRUSH
    _gdi32.Ellipse.argtypes = [wintypes.HDC, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_int]
    _gdi32.DeleteObject.argtypes = [wintypes.HGDIOBJ]
    _gdi32.DeleteDC.argtypes = [wintypes.HDC]

    _gdiplus.GdiplusStartup.argtypes = [
        ctypes.POINTER(ULONG_PTR),
        ctypes.POINTER(GdiplusStartupInput),
        ctypes.c_void_p,
    ]
    _gdiplus.GdipCreateBitmapFromHBITMAP.argtypes = [
        wintypes.HBITMAP,
        wintypes.HPALETTE,
        ctypes.POINTER(ctypes.c_void_p),
    ]
    _gdiplus.GdipSaveImageToStream.argtypes = [
        ctypes.c_void_p,
        ctypes.c_void_p,
        ctypes.POINTER(GUID),
        ctypes.POINTER(EncoderParameters),
    ]
    _gdiplus.GdipDisposeImage.argtypes = [ctypes.c_void_p]

    _ole32.CreateStreamOnHGlobal.argtypes = [wintypes.HGLOBAL, wintypes.BOOL, ctypes.POINTER(ctypes.c_void_p)]
    _ole32.GetHGlobalFromStream.argtypes = [ctypes.c_void_p, ctypes.POINTER(wintypes.HGLOBAL)]

    _kernel32.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
    _kernel32.OpenProcess.restype = wintypes.HANDLE
    _kernel32.CloseHandle.argtypes = [wintypes.HANDLE]
    _kernel32.QueryFullProcessImageNameW.argtypes = [
        wintypes.HANDLE,
        wintypes.DWORD,
        wintypes.LPWSTR,
        ctypes.POINTER(wintypes.DWORD),
    ]
    _kernel32.QueryFullProcessImageNameW.restype = wintypes.BOOL
    _kernel32.CreateToolhelp32Snapshot.argtypes = [wintypes.DWORD, wintypes.DWORD]
    _kernel32.CreateToolhelp32Snapshot.restype = wintypes.HANDLE
    _kernel32.Process32FirstW.argtypes = [wintypes.HANDLE, ctypes.POINTER(PROCESSENTRY32W)]
    _kernel32.Process32FirstW.restype = wintypes.BOOL
    _kernel32.Process32NextW.argtypes = [wintypes.HANDLE, ctypes.POINTER(PROCESSENTRY32W)]
    _kernel32.Process32NextW.restype = wintypes.BOOL
    _kernel32.GlobalMemoryStatusEx.argtypes = [ctypes.POINTER(MEMORYSTATUSEX)]
    _kernel32.GlobalMemoryStatusEx.restype = wintypes.BOOL
    _kernel32.GlobalAlloc.argtypes = [wintypes.UINT, ctypes.c_size_t]
    _kernel32.GlobalAlloc.restype = wintypes.HGLOBAL
    _kernel32.GlobalLock.argtypes = [wintypes.HGLOBAL]
    _kernel32.GlobalLock.restype = ctypes.c_void_p
    _kernel32.GlobalUnlock.argtypes = [wintypes.HGLOBAL]
    _kernel32.GlobalSize.argtypes = [wintypes.HGLOBAL]
    _kernel32.GlobalSize.restype = ctypes.c_size_t
    _kernel32.GlobalFree.argtypes = [wintypes.HGLOBAL]

    _EnumProc = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
    _user32.EnumWindows.argtypes = [_EnumProc, wintypes.LPARAM]

    _powrprof.SetSuspendState.argtypes = [wintypes.BOOLEAN, wintypes.BOOLEAN, wintypes.BOOLEAN]
    _powrprof.SetSuspendState.restype = wintypes.BOOLEAN

    _gdip_token = ULONG_PTR(0)
    _gdip_input = GdiplusStartupInput(1, None, False, False)
    _gdiplus.GdiplusStartup(ctypes.byref(_gdip_token), ctypes.byref(_gdip_input), None)


def _send_inputs(inputs: list) -> None:
    if not IS_WINDOWS or not inputs:
        return
    arr = (INPUT * len(inputs))(*inputs)
    _user32.SendInput(len(inputs), arr, ctypes.sizeof(INPUT))


def _vk_input(vk: int, key_up: bool = False) -> "INPUT":
    flags = KEYEVENTF_KEYUP if key_up else 0
    return INPUT(
        type=INPUT_KEYBOARD,
        u=_INPUT_UNION(ki=KEYBDINPUT(wVk=vk, wScan=0, dwFlags=flags, time=0, dwExtraInfo=0)),
    )


def _unicode_inputs(text: str) -> list:
    """Формирует события нажатия символов в UTF-16 (не зависит от текущей раскладки клавиатуры ПК)."""
    events = []
    utf16_bytes = text.encode("utf-16-le")
    for i in range(0, len(utf16_bytes), 2):
        code_unit = int.from_bytes(utf16_bytes[i : i + 2], "little")
        events.append(
            INPUT(
                type=INPUT_KEYBOARD,
                u=_INPUT_UNION(
                    ki=KEYBDINPUT(wVk=0, wScan=code_unit, dwFlags=KEYEVENTF_UNICODE, time=0, dwExtraInfo=0)
                ),
            )
        )
        events.append(
            INPUT(
                type=INPUT_KEYBOARD,
                u=_INPUT_UNION(
                    ki=KEYBDINPUT(
                        wVk=0,
                        wScan=code_unit,
                        dwFlags=KEYEVENTF_UNICODE | KEYEVENTF_KEYUP,
                        time=0,
                        dwExtraInfo=0,
                    )
                ),
            )
        )
    return events


def _mouse_input(dx: int = 0, dy: int = 0, data: int = 0, flags: int = 0) -> "INPUT":
    return INPUT(
        type=INPUT_MOUSE,
        u=_INPUT_UNION(
            mi=MOUSEINPUT(dx=dx, dy=dy, mouseData=data & 0xFFFFFFFF, dwFlags=flags, time=0, dwExtraInfo=0)
        ),
    )


_cached_pen = None
_cached_brush = None

def _get_cached_pen():
    global _cached_pen
    if _cached_pen is None:
        _cached_pen = _gdi32.CreatePen(0, 2, 0x00FFFFFF)
    return _cached_pen

def _get_cached_brush():
    global _cached_brush
    if _cached_brush is None:
        _cached_brush = _gdi32.CreateSolidBrush(0x003030FF)
    return _cached_brush

def capture_screen_jpeg(max_width: int = 960, quality: int = 60, all_monitors: bool = False) -> bytes:
    """Делает снимок экрана Windows с отрисовкой позиции курсора и сжимает его в JPEG в памяти."""
    global _last_jpeg, _last_jpeg_ts, _last_jpeg_key
    if not IS_WINDOWS:
        return b""
    max_width = max(320, min(1920, max_width))
    quality = max(20, min(95, quality))
    req_key = (max_width, quality, all_monitors)

    with _screen_lock:
        now = time.monotonic()
        if _last_jpeg and _last_jpeg_key == req_key and (now - _last_jpeg_ts) < SCREEN_CACHE_TTL:
            return _last_jpeg

        if all_monitors:
            src_x = _user32.GetSystemMetrics(76)  # SM_XVIRTUALSCREEN
            src_y = _user32.GetSystemMetrics(77)  # SM_YVIRTUALSCREEN
            src_w = _user32.GetSystemMetrics(78)  # SM_CXVIRTUALSCREEN
            src_h = _user32.GetSystemMetrics(79)  # SM_CYVIRTUALSCREEN
        else:
            src_x, src_y = 0, 0
            src_w = _user32.GetSystemMetrics(0)
            src_h = _user32.GetSystemMetrics(1)

        if src_w <= 0 or src_h <= 0:
            return b""

        if src_w > max_width:
            dst_w = max_width
            dst_h = max(1, int(src_h * (max_width / src_w)))
        else:
            dst_w, dst_h = src_w, src_h

        hdc_screen = _user32.GetDC(0)
        if not hdc_screen:
            return b""
        hdc_mem = _gdi32.CreateCompatibleDC(hdc_screen)
        hbmp = _gdi32.CreateCompatibleBitmap(hdc_screen, dst_w, dst_h)
        old_obj = _gdi32.SelectObject(hdc_mem, hbmp)

        gp_bitmap = ctypes.c_void_p()
        stream = ctypes.c_void_p()
        try:
            if src_w == dst_w and src_h == dst_h:
                _gdi32.BitBlt(hdc_mem, 0, 0, dst_w, dst_h, hdc_screen, src_x, src_y, 0x00CC0020)
            else:
                _gdi32.SetStretchBltMode(hdc_mem, 3)
                _gdi32.StretchBlt(hdc_mem, 0, 0, dst_w, dst_h, hdc_screen, src_x, src_y, src_w, src_h, 0x00CC0020)

            # Рисуем курсор мыши
            pt = wintypes.POINT()
            if _user32.GetCursorPos(ctypes.byref(pt)):
                cx = int((pt.x - src_x) * dst_w / src_w)
                cy = int((pt.y - src_y) * dst_h / src_h)
                pen = _get_cached_pen()
                brush = _get_cached_brush()
                old_pen = _gdi32.SelectObject(hdc_mem, pen)
                old_brush = _gdi32.SelectObject(hdc_mem, brush)
                _gdi32.Ellipse(hdc_mem, cx - 6, cy - 6, cx + 6, cy + 6)
                _gdi32.SelectObject(hdc_mem, old_pen)
                _gdi32.SelectObject(hdc_mem, old_brush)

            if _gdiplus.GdipCreateBitmapFromHBITMAP(hbmp, 0, ctypes.byref(gp_bitmap)) != 0 or not gp_bitmap:
                return b""

            if _ole32.CreateStreamOnHGlobal(None, True, ctypes.byref(stream)) != 0 or not stream:
                return b""

            q_val = wintypes.ULONG(quality)
            params = EncoderParameters()
            params.Count = 1
            params.Parameter[0].Guid = GUID_QUALITY
            params.Parameter[0].NumberOfValues = 1
            params.Parameter[0].Type = 4
            params.Parameter[0].Value = ctypes.cast(ctypes.byref(q_val), ctypes.c_void_p)

            if _gdiplus.GdipSaveImageToStream(gp_bitmap, stream, ctypes.byref(CLSID_JPEG), ctypes.byref(params)) != 0:
                return b""

            hglobal = wintypes.HGLOBAL()
            if _ole32.GetHGlobalFromStream(stream, ctypes.byref(hglobal)) != 0 or not hglobal:
                return b""

            size = _kernel32.GlobalSize(hglobal)
            if size <= 0:
                return b""
            ptr = _kernel32.GlobalLock(hglobal)
            if not ptr:
                return b""
            try:
                jpg_bytes = ctypes.string_at(ptr, size)
                _last_jpeg = jpg_bytes
                _last_jpeg_ts = time.monotonic()
                _last_jpeg_key = req_key
                return jpg_bytes
            finally:
                _kernel32.GlobalUnlock(hglobal)
        finally:
            if stream:
                vtbl = ctypes.cast(stream, ctypes.POINTER(ctypes.POINTER(ctypes.c_void_p))).contents
                release_fn = ctypes.WINFUNCTYPE(wintypes.ULONG, ctypes.c_void_p)(vtbl[2])
                release_fn(stream)
            if gp_bitmap:
                _gdiplus.GdipDisposeImage(gp_bitmap)
            _gdi32.SelectObject(hdc_mem, old_obj)
            _gdi32.DeleteObject(hbmp)
            _gdi32.DeleteDC(hdc_mem)
            _user32.ReleaseDC(0, hdc_screen)


def get_clipboard_text() -> str:
    if not IS_WINDOWS:
        return ""
    if not _user32.OpenClipboard(0):
        return ""
    try:
        hmem = _user32.GetClipboardData(CF_UNICODETEXT)
        if not hmem:
            return ""
        ptr = _kernel32.GlobalLock(hmem)
        if not ptr:
            return ""
        try:
            return ctypes.wstring_at(ptr)
        finally:
            _kernel32.GlobalUnlock(hmem)
    except Exception:
        return ""
    finally:
        _user32.CloseClipboard()


def set_clipboard_text(text: str) -> bool:
    if not IS_WINDOWS:
        return False
    if not _user32.OpenClipboard(0):
        return False
    try:
        _user32.EmptyClipboard()
        raw = (text + "\0").encode("utf-16-le")
        hmem = _kernel32.GlobalAlloc(0x0042, len(raw))
        if not hmem:
            return False
        ptr = _kernel32.GlobalLock(hmem)
        if not ptr:
            _kernel32.GlobalFree(hmem)
            return False
        ctypes.memmove(ptr, raw, len(raw))
        _kernel32.GlobalUnlock(hmem)
        if not _user32.SetClipboardData(CF_UNICODETEXT, hmem):
            _kernel32.GlobalFree(hmem)
            return False
        return True
    except Exception:
        return False
    finally:
        _user32.CloseClipboard()

_last_idle = 0
_last_kernel = 0
_last_user = 0
_last_cpu_percent = 0
_last_cpu_time = 0.0
_cpu_lock = threading.Lock()

def get_cpu_usage_percent() -> int:
    global _last_idle, _last_kernel, _last_user, _last_cpu_percent, _last_cpu_time
    if not IS_WINDOWS:
        return 12
    with _cpu_lock:
        now = time.monotonic()
        if now - _last_cpu_time < 0.8:
            return _last_cpu_percent

        try:
            idle = FILETIME()
            kernel = FILETIME()
            user = FILETIME()
            if not _kernel32.GetSystemTimes(ctypes.byref(idle), ctypes.byref(kernel), ctypes.byref(user)):
                return _last_cpu_percent

            i = (idle.dwHighDateTime << 32) | idle.dwLowDateTime
            k = (kernel.dwHighDateTime << 32) | kernel.dwLowDateTime
            u = (user.dwHighDateTime << 32) | user.dwLowDateTime

            if _last_idle > 0:
                idle_diff = i - _last_idle
                kernel_diff = k - _last_kernel
                user_diff = u - _last_user
                total = kernel_diff + user_diff
                if total > 0:
                    _last_cpu_percent = max(0, min(100, round((1.0 - (idle_diff / float(total))) * 100)))

            _last_idle, _last_kernel, _last_user = i, k, u
            _last_cpu_time = now
        except Exception:
            pass
        return _last_cpu_percent

def get_ram_info() -> dict:
    if not IS_WINDOWS:
        return {"percent": 40, "totalGb": 16.0, "usedGb": 6.4}
    try:
        stat = MEMORYSTATUSEX()
        stat.dwLength = ctypes.sizeof(MEMORYSTATUSEX)
        if _kernel32.GlobalMemoryStatusEx(ctypes.byref(stat)):
            total_gb = round(stat.ullTotalPhys / (1024**3), 1)
            used_gb = round((stat.ullTotalPhys - stat.ullAvailPhys) / (1024**3), 1)
            return {
                "percent": int(stat.dwMemoryLoad),
                "totalGb": total_gb,
                "usedGb": used_gb
            }
    except Exception:
        pass
    return {"percent": 0, "totalGb": 0.0, "usedGb": 0.0}

def get_system_uptime_seconds() -> int:
    if not IS_WINDOWS:
        return 7200
    try:
        return int(_kernel32.GetTickCount64() // 1000)
    except Exception:
        return 0

def get_system_disks() -> list:
    disks = []
    if IS_WINDOWS:
        drives = []
        for letter in "CDEFGHIJKLMNOPQRSTUVWXYZ":
            if os.path.exists(f"{letter}:\\"):
                drives.append(f"{letter}:")
    else:
        drives = ["/"]

    for d in drives:
        path = f"{d}\\" if IS_WINDOWS else d
        try:
            total, used, free = shutil.disk_usage(path)
            total_gb = round(total / (1024**3), 1)
            free_gb = round(free / (1024**3), 1)
            pct = round((used / float(total)) * 100) if total > 0 else 0
            disks.append({
                "drive": d,
                "totalGb": total_gb,
                "freeGb": free_gb,
                "usedPercent": pct
            })
        except Exception:
            pass
    return disks

def get_ram_usage_percent() -> int:
    if not IS_WINDOWS:
        return 0
    try:
        stat = MEMORYSTATUSEX()
        stat.dwLength = ctypes.sizeof(MEMORYSTATUSEX)
        if _kernel32.GlobalMemoryStatusEx(ctypes.byref(stat)):
            return int(stat.dwMemoryLoad)
    except Exception:
        pass
    return 0


def invalidate_process_cache() -> None:
    global _proc_cache_ts
    with _proc_lock:
        _proc_cache_ts = 0.0


def running_processes() -> set:
    global _proc_cache, _proc_cache_ts
    if not IS_WINDOWS:
        return set()

    now = time.monotonic()
    with _proc_lock:
        if now - _proc_cache_ts < PROCESS_CACHE_TTL:
            return set(_proc_cache)

    names = set()
    try:
        h_snap = _kernel32.CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0)
        if h_snap and h_snap != wintypes.HANDLE(-1).value:
            try:
                pe = PROCESSENTRY32W()
                pe.dwSize = ctypes.sizeof(PROCESSENTRY32W)
                if _kernel32.Process32FirstW(h_snap, ctypes.byref(pe)):
                    while True:
                        names.add(pe.szExeFile.lower())
                        if not _kernel32.Process32NextW(h_snap, ctypes.byref(pe)):
                            break
            finally:
                _kernel32.CloseHandle(h_snap)
    except Exception:
        pass

    if names:
        with _proc_lock:
            _proc_cache = names
            _proc_cache_ts = time.monotonic()
    return set(names)


def _window_process_info(hwnd) -> tuple:
    pid = wintypes.DWORD()
    _user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
    handle = _kernel32.OpenProcess(0x1000, False, pid.value)
    if not handle:
        return "", ""
    try:
        buf = ctypes.create_unicode_buffer(1024)
        size = wintypes.DWORD(len(buf))
        if _kernel32.QueryFullProcessImageNameW(handle, 0, buf, ctypes.byref(size)):
            full_path = buf.value
            return os.path.basename(full_path).lower(), full_path
        return "", ""
    finally:
        _kernel32.CloseHandle(handle)


def _window_process_name(hwnd) -> str:
    return _window_process_info(hwnd)[0]


def _window_title(hwnd) -> str:
    if not hwnd:
        return ""
    length = _user32.GetWindowTextLengthW(hwnd)
    if length <= 0:
        return ""
    buf = ctypes.create_unicode_buffer(length + 1)
    _user32.GetWindowTextW(hwnd, buf, length + 1)
    return buf.value


def get_active_window_title() -> str:
    if not IS_WINDOWS:
        return ""
    try:
        return _window_title(_user32.GetForegroundWindow())
    except Exception:
        return ""


_IGNORED_WINDOW_TITLES = {
    "program manager",
    "microsoft text input application",
    "settings",
    "параметры",
    "windows input experience",
}


def list_open_windows() -> list:
    if not IS_WINDOWS:
        return []
    fg_hwnd = _user32.GetForegroundWindow()
    items = []

    def callback(hwnd, _lparam):
        try:
            if _user32.IsWindowVisible(hwnd):
                title = _window_title(hwnd).strip()
                if title and title.lower() not in _IGNORED_WINDOW_TITLES:
                    proc, exe_path = _window_process_info(hwnd)
                    if proc and proc not in ("searchhost.exe", "shellexperiencehost.exe", "textinputhost.exe"):
                        items.append(
                            {
                                "hwnd": int(hwnd),
                                "title": title,
                                "process": proc,
                                "path": exe_path,
                                "active": bool(hwnd == fg_hwnd),
                                "minimized": bool(_user32.IsIconic(hwnd)),
                            }
                        )
        except Exception:
            pass
        return True

    _user32.EnumWindows(_EnumProc(callback), 0)
    return items[:25]


def control_window(body: str):
    if not IS_WINDOWS:
        return 400, "только для Windows"
    try:
        data = json.loads(body) if body.startswith("{") else {}
        hwnd = int(data.get("hwnd", 0))
        action = str(data.get("action", "focus"))
    except Exception:
        return 400, "некорректные параметры окна"

    if not hwnd:
        return 400, "не указан hwnd"
    try:
        if action == "focus":
            if _user32.IsIconic(hwnd):
                _user32.ShowWindow(hwnd, 9)
            _send_inputs([_vk_input(0x12, False), _vk_input(0x12, True)])
            _user32.SetForegroundWindow(hwnd)
            return 200, "ok"
        if action == "minimize":
            _user32.ShowWindow(hwnd, 6)
            return 200, "ok"
        if action == "maximize":
            if _user32.IsZoomed(hwnd):
                _user32.ShowWindow(hwnd, 9)
            else:
                _user32.ShowWindow(hwnd, 3)
            return 200, "ok"
        if action == "close":
            _user32.PostMessageW(hwnd, WM_CLOSE, 0, 0)
            return 200, "ok"
        return 404, "неизвестное действие"
    except Exception as e:
        return 500, f"ошибка управления окном: {e}"


_current_volume = 50
_timer_generation = 0

def get_real_windows_volume() -> int:
    global _current_volume
    if not IS_WINDOWS:
        return _current_volume
    try:
        vol = wintypes.DWORD()
        res = ctypes.windll.winmm.waveOutGetVolume(0, ctypes.byref(vol))
        if res == 0 and vol.value > 0:
            left = vol.value & 0xFFFF
            return max(0, min(100, round((left / 65535.0) * 100)))
    except Exception:
        pass
    return _current_volume

def set_exact_volume(body: str):
    global _current_volume
    if not IS_WINDOWS:
        return 400, "только для Windows"
    try:
        level = max(0, min(100, int(body.strip())))
    except ValueError:
        return 400, "уровень громкости должен быть числом 0..100"
    try:
        events = []
        for _ in range(50):
            events.append(_vk_input(0xAE, False))
            events.append(_vk_input(0xAE, True))
        steps_up = round(level / 2.0)
        for _ in range(steps_up):
            events.append(_vk_input(0xAF, False))
            events.append(_vk_input(0xAF, True))
        _send_inputs(events)
        _current_volume = level
        return 200, "ok"
    except Exception as e:
        return 500, f"не удалось выставить громкость: {e}"


def _foreground_is_browser() -> bool:
    hwnd = _user32.GetForegroundWindow()
    return bool(hwnd) and _window_process_name(hwnd) == BROWSER_PROCESS


def _find_browser_window():
    candidates = []

    def callback(hwnd, _lparam):
        try:
            if _user32.IsWindowVisible(hwnd):
                title = _window_title(hwnd)
                if title and _window_process_name(hwnd) == BROWSER_PROCESS:
                    candidates.append((hwnd, title.lower()))
        except Exception:
            pass
        return True

    _user32.EnumWindows(_EnumProc(callback), 0)
    if not candidates:
        return None
    for hwnd, title in candidates:
        if "youtube" in title:
            return hwnd
    return candidates[0][0]


def focus_browser() -> bool:
    if not IS_WINDOWS:
        return False
    try:
        if _foreground_is_browser():
            return True
        hwnd = _find_browser_window()
        if not hwnd:
            return False
        if _user32.IsIconic(hwnd):
            _user32.ShowWindow(hwnd, 9)
        _send_inputs([_vk_input(0x12, False), _vk_input(0x12, True)])
        _user32.SetForegroundWindow(hwnd)
        for _ in range(10):
            time.sleep(0.05)
            if _foreground_is_browser():
                return True
        return False
    except Exception as e:
        log(f"[!] focus_browser: {e}")
        return False


def turn_off_monitor():
    if not IS_WINDOWS:
        return 400, "только для Windows"
    try:
        _user32.PostMessageW(0xFFFF, 0x0112, 0xF170, 2)
        return 200, "ok"
    except Exception as e:
        return 500, f"не удалось погасить экран: {e}"


def sleep_pc() -> None:
    if not IS_WINDOWS:
        return
    try:
        time.sleep(0.3)
        _powrprof.SetSuspendState(0, 0, 0)
    except Exception as e:
        log(f"[!] Не удалось перевести ПК в сон: {e}")


def send_media_key(action: str):
    if not IS_WINDOWS:
        return 400, "клавиши поддерживаются только на Windows"
    if action not in MEDIA_KEYS and action not in COMBO_KEYS:
        return 404, "неизвестная команда"
    try:
        if action in BROWSER_ACTIONS:
            if not focus_browser():
                return 409, "Brave не открыт или не вышел на передний план. Открой видео кнопкой «На ПК»."
            time.sleep(0.08)

        if action in COMBO_KEYS:
            keys = COMBO_KEYS[action]
            events = [_vk_input(vk, False) for vk in keys] + [_vk_input(vk, True) for vk in reversed(keys)]
            _send_inputs(events)
            return 200, "ok"

        vk, repeats = MEDIA_KEYS[action]
        events = []
        for _ in range(repeats):
            events.append(_vk_input(vk, False))
            events.append(_vk_input(vk, True))
        _send_inputs(events)
        return 200, "ok"
    except Exception as e:
        return 500, f"ошибка отправки клавиши: {e}"


def handle_type_text(body: str):
    if not IS_WINDOWS:
        return 400, "ввод текста поддерживается только на Windows"
    text = body
    press_enter = False
    if body.startswith("{"):
        try:
            data = json.loads(body)
            text = str(data.get("text", ""))
            press_enter = bool(data.get("enter", False))
        except Exception:
            pass
    if not text and not press_enter:
        return 400, "пустой текст"
    if len(text) > 1000:
        text = text[:1000]
    try:
        events = _unicode_inputs(text)
        if press_enter:
            events.append(_vk_input(0x0D, False))
            events.append(_vk_input(0x0D, True))
        _send_inputs(events)
        return 200, "ok"
    except Exception as e:
        return 500, f"ошибка ввода текста: {e}"


def _abs_coords(data: dict) -> tuple:
    nx = max(0.0, min(1.0, float(data.get("nx", 0.5))))
    ny = max(0.0, min(1.0, float(data.get("ny", 0.5))))
    return int(nx * 65535), int(ny * 65535)


def handle_mouse(body: str):
    if not IS_WINDOWS:
        return 400, "мышь поддерживается только на Windows"
    try:
        data = json.loads(body) if body.startswith("{") else {}
    except Exception:
        return 400, "некорректный JSON"

    action = str(data.get("action", "move"))
    all_mons = bool(data.get("allMonitors", False))
    try:
        if action == "move":
            dx = max(-2000, min(2000, int(data.get("dx", 0))))
            dy = max(-2000, min(2000, int(data.get("dy", 0))))
            if dx or dy:
                _send_inputs([_mouse_input(dx=dx, dy=dy, flags=MOUSEEVENTF_MOVE)])
            return 200, "ok"

        if action in ("abs_move", "abs_click", "abs_double", "abs_right"):
            ax, ay = _abs_coords(data)
            move_flags = MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE
            if all_mons:
                move_flags |= MOUSEEVENTF_VIRTUALDESK
            events = [_mouse_input(dx=ax, dy=ay, flags=move_flags)]
            if action == "abs_click":
                events += [
                    _mouse_input(flags=MOUSEEVENTF_LEFTDOWN),
                    _mouse_input(flags=MOUSEEVENTF_LEFTUP),
                ]
            elif action == "abs_double":
                click = [
                    _mouse_input(flags=MOUSEEVENTF_LEFTDOWN),
                    _mouse_input(flags=MOUSEEVENTF_LEFTUP),
                ]
                events += click + click
            elif action == "abs_right":
                events += [
                    _mouse_input(flags=MOUSEEVENTF_RIGHTDOWN),
                    _mouse_input(flags=MOUSEEVENTF_RIGHTUP),
                ]
            _send_inputs(events)
            return 200, "ok"

        if action == "left":
            _send_inputs(
                [
                    _mouse_input(flags=MOUSEEVENTF_LEFTDOWN),
                    _mouse_input(flags=MOUSEEVENTF_LEFTUP),
                ]
            )
            return 200, "ok"

        if action == "double":
            click = [
                _mouse_input(flags=MOUSEEVENTF_LEFTDOWN),
                _mouse_input(flags=MOUSEEVENTF_LEFTUP),
            ]
            _send_inputs(click + click)
            return 200, "ok"

        if action == "right":
            _send_inputs(
                [
                    _mouse_input(flags=MOUSEEVENTF_RIGHTDOWN),
                    _mouse_input(flags=MOUSEEVENTF_RIGHTUP),
                ]
            )
            return 200, "ok"

        if action == "middle":
            _send_inputs(
                [
                    _mouse_input(flags=MOUSEEVENTF_MIDDLEDOWN),
                    _mouse_input(flags=MOUSEEVENTF_MIDDLEUP),
                ]
            )
            return 200, "ok"

        if action == "scroll":
            steps = max(-20, min(20, int(data.get("dy", 0))))
            if steps:
                _send_inputs([_mouse_input(data=steps * 120, flags=MOUSEEVENTF_WHEEL)])
            return 200, "ok"

        return 404, "неизвестное действие мыши"
    except Exception as e:
        return 500, f"ошибка мыши: {e}"


def open_url_on_pc(raw_input: str):
    clean = raw_input.strip()
    if not clean:
        return 400, "пустой запрос"

    if clean.startswith("http://") or clean.startswith("https://"):
        target_url = clean
    elif "." in clean and " " not in clean:
        target_url = "https://" + clean
    else:
        target_url = f"https://www.youtube.com/results?search_query={quote_plus(clean)}"

    brave_exe = os.path.expandvars(BRAVE)
    try:
        if os.path.exists(brave_exe):
            workdir = os.path.dirname(brave_exe) or None
            subprocess.Popen([brave_exe, target_url], cwd=workdir, creationflags=DETACHED_FLAGS)
        elif IS_WINDOWS:
            os.startfile(target_url)  # type: ignore[attr-defined]
        else:
            return 404, "браузер не найден"
        invalidate_process_cache()
        if IS_WINDOWS:
            timer = threading.Timer(2.0, focus_browser)
            timer.daemon = True
            timer.start()
        return 200, "ok"
    except Exception as e:
        return 500, f"не удалось открыть ссылку: {e}"


def launch_app(app: dict):
    args = [os.path.expandvars(a) for a in app["launch"]]
    exe = args[0]
    if not os.path.exists(exe):
        return 404, f"не найден файл: {exe}"
    workdir = os.path.dirname(exe) or None
    try:
        subprocess.Popen(args, cwd=workdir, creationflags=DETACHED_FLAGS)
        invalidate_process_cache()
        return 200, "ok"
    except OSError as e:
        return 500, f"не удалось запустить: {e}"


def close_app(app: dict):
    proc = app.get("process")
    if not proc:
        return 400, "это приложение нельзя закрыть"
    cmd = ["taskkill", "/IM", proc]
    if app.get("force"):
        cmd.append("/F")
    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            timeout=10,
            creationflags=CREATE_NO_WINDOW,
        )
    except OSError as e:
        return 500, f"не удалось выполнить taskkill: {e}"
    invalidate_process_cache()
    if result.returncode != 0:
        return 409, "программа не запущена или не закрылась"
    return 200, "ok"


def add_custom_app(body: str):
    """Добавляет новую программу в config.json прямо с телефона (автоматически извлекая имя процесса)."""
    global CFG
    try:
        data = json.loads(body)
    except Exception:
        return 400, "некорректный JSON"

    title = str(data.get("title", "")).strip()
    launch_path = str(data.get("launch", "")).strip()
    process = str(data.get("process", "")).strip()
    force = bool(data.get("force", False))

    if not launch_path and not title:
        return 400, "укажи название или путь к программе"

    # Если указан только путь, название берём из имени файла
    if not title and launch_path:
        base = os.path.basename(launch_path)
        title = os.path.splitext(base)[0].capitalize()

    # Если процесс не указан, автоматически берем exe-файл из пути
    if not process and launch_path:
        base = os.path.basename(launch_path)
        if base.lower().endswith(".exe"):
            process = base

    # Если указано только название (например, Telegram), формируем запуск
    if not launch_path and title:
        launch_path = title if title.lower().endswith(".exe") else f"{title}.exe"
        if not process:
            process = launch_path

    app_id = str(data.get("id", "")).strip()
    if not app_id:
        app_id = "app_" + secrets.token_hex(4)

    new_app = {
        "id": app_id,
        "title": title,
        "launch": [launch_path],
        "process": process or None,
        "force": force,
    }

    with _cfg_lock:
        existing = [a for a in CFG.get("apps", []) if a.get("id") != app_id]
        existing.append(new_app)
        CFG["apps"] = existing
        save_config(CFG)

    reload_config_if_needed()
    return 200, "ok"


def delete_custom_app(app_id: str):
    """Удаляет программу из config.json."""
    global CFG
    with _cfg_lock:
        existing = [a for a in CFG.get("apps", []) if a.get("id") != app_id]
        if len(existing) == len(CFG.get("apps", [])):
            return 404, "программа не найдена"
        CFG["apps"] = existing
        save_config(CFG)
    reload_config_if_needed()
    return 200, "ok"


def get_apk_manifest_version(apk_path):
    """Извлекает реальный versionCode и versionName прямо из файла AndroidManifest.xml внутри APK."""
    import zipfile, struct
    try:
        with zipfile.ZipFile(apk_path, "r") as zf:
            if "AndroidManifest.xml" not in zf.namelist():
                return None, None
            data = zf.read("AndroidManifest.xml")
        if len(data) < 16:
            return None, None
        chunk_type, chunk_size = struct.unpack("<II", data[8:16])
        if chunk_type != 0x001C0001:
            return None, None
        str_count, _, flags, strings_start, _ = struct.unpack("<IIIII", data[16:36])
        is_utf8 = bool(flags & (1 << 8))
        offsets = struct.unpack(f"<{str_count}I", data[36:36 + str_count * 4])
        pool_bytes = data[8 + strings_start:]
        strings = []
        for off in offsets:
            p = off
            if is_utf8:
                if pool_bytes[p] & 0x80:
                    p += 2
                else:
                    p += 1
                if pool_bytes[p] & 0x80:
                    u8len = ((pool_bytes[p] & 0x7F) << 8) | pool_bytes[p + 1]
                    p += 2
                else:
                    u8len = pool_bytes[p]
                    p += 1
                strings.append(pool_bytes[p:p + u8len].decode("utf-8", "replace"))
            else:
                l = struct.unpack("<H", pool_bytes[p:p + 2])[0]
                if l & 0x8000:
                    l = ((l & 0x7FFF) << 16) | struct.unpack("<H", pool_bytes[p + 2:p + 4])[0]
                    p += 4
                else:
                    p += 2
                strings.append(pool_bytes[p:p + l * 2].decode("utf-16le", "replace"))
        version_code_idx = strings.index("versionCode") if "versionCode" in strings else None
        version_name_idx = strings.index("versionName") if "versionName" in strings else None
        offset = 8 + chunk_size
        v_code, v_name = None, None
        while offset < len(data) - 8:
            tag_type, tag_size = struct.unpack("<II", data[offset:offset + 8])
            if tag_type == 0x00100102: # START_TAG
                attr_count = struct.unpack("<H", data[offset + 28:offset + 30])[0]
                attr_offset = offset + 36
                for _ in range(attr_count):
                    _, name, val_str, _, val_data = struct.unpack("<IIIII", data[attr_offset:attr_offset + 20])
                    if name == version_code_idx:
                        v_code = val_data
                    elif name == version_name_idx:
                        if val_str < len(strings):
                            v_name = strings[val_str]
                    attr_offset += 20
                break
            offset += tag_size
            if tag_size == 0:
                break
        return v_code, v_name
    except Exception:
        return None, None


def get_latest_apk_info():
    """Проверяет наличие APK в папке updates/ и возвращает версию/дату."""
    if not os.path.exists(UPDATES_DIR):
        os.makedirs(UPDATES_DIR, exist_ok=True)
        return None

    apk_files = [f for f in os.listdir(UPDATES_DIR) if f.lower().endswith(".apk")]
    if not apk_files:
        return None

    # Берём самый свежий APK по дате изменения
    apk_files.sort(key=lambda f: os.path.getmtime(os.path.join(UPDATES_DIR, f)), reverse=True)
    latest = apk_files[0]
    path = os.path.join(UPDATES_DIR, latest)
    mtime = int(os.path.getmtime(path))
    size = os.path.getsize(path)

    import hashlib
    with open(path, "rb") as f:
        file_hash = hashlib.sha256(f.read()).hexdigest()

    v_info_path = os.path.join(UPDATES_DIR, "version.json")

    # ВСЕГДА извлекаем настоящие versionCode и versionName напрямую из бинарного AndroidManifest внутри APK!
    parsed_c, parsed_n = get_apk_manifest_version(path)
    if parsed_c is not None and parsed_c > 0:
        v_code = parsed_c
        v_name = parsed_n or f"2.{parsed_c}"
    else:
        # Резервный вариант: если парсер бинарного AXML дал сбой, читаем сохранённый version.json
        v_code = 1
        v_name = "1.0"
        if os.path.exists(v_info_path):
            try:
                with open(v_info_path, "r", encoding="utf-8") as f:
                    v_data = json.load(f)
                    v_code = int(v_data.get("versionCode", 1))
                    v_name = str(v_data.get("versionName", "1.0"))
            except Exception:
                pass

    # Автоматически обновляем version.json с реальными версией, хешем и датой из APK
    try:
        with open(v_info_path, "w", encoding="utf-8") as f:
            json.dump({
                "versionCode": v_code,
                "versionName": v_name,
                "filename": latest,
                "hash": file_hash,
                "size": size,
                "updated": time.strftime("%Y-%m-%d %H:%M:%S")
            }, f, indent=2)
    except Exception:
        pass

    return {
        "filename": latest,
        "path": path,
        "versionCode": v_code,
        "versionName": v_name,
        "hash": file_hash,
        "size": size,
    }


def set_custom_timer(minutes: int, action: str):
    """Устанавливает таймер выключения или сна на произвольное количество минут с защитой от гонки потоков."""
    global _shutdown_deadline, _timer_generation
    if minutes <= 0:
        return 400, "время должно быть больше 0 минут"
    sec = minutes * 60
    _shutdown_deadline = time.time() + sec
    _timer_generation += 1
    current_gen = _timer_generation

    if action == "sleep":
        subprocess.run(["shutdown", "/a"], capture_output=True, timeout=5, creationflags=CREATE_NO_WINDOW)
        def worker(gen_id, deadline):
            global _shutdown_deadline, _timer_generation
            while time.time() < deadline:
                time.sleep(1)
                if _timer_generation != gen_id or _shutdown_deadline <= 0:
                    return
            if _timer_generation == gen_id:
                sleep_pc()
                _shutdown_deadline = 0.0
        t = threading.Thread(target=worker, args=(current_gen, _shutdown_deadline), daemon=True)
        t.start()
        log(f"Установлен таймер сна на {minutes} мин ({sec} сек, gen #{current_gen})")
        return 200, f"Таймер сна на {minutes} мин запущен"
    else:
        run_power(["shutdown", "/s", "/t", str(sec)], sec)
        return 200, f"Таймер выключения на {minutes} мин запущен"


def run_power(cmd: list, timer_sec) -> None:
    global _shutdown_deadline
    log(f"Выполняю: {' '.join(cmd)}")
    try:
        if cmd[0] == "shutdown" and cmd[1] != "/a":
            subprocess.run(["shutdown", "/a"], capture_output=True, timeout=5, creationflags=CREATE_NO_WINDOW)
        subprocess.Popen(cmd, creationflags=CREATE_NO_WINDOW)
        if timer_sec == 0:
            _shutdown_deadline = 0.0
        elif isinstance(timer_sec, int) and timer_sec > 10:
            _shutdown_deadline = time.time() + timer_sec
    except Exception as e:
        log(f"[!] Не удалось выполнить команду питания: {e}")


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _authorized(self) -> bool:
        reload_config_if_needed()
        given = self.headers.get("X-Token", "")
        return hmac.compare_digest(given.encode("utf-8"), TOKEN.encode("utf-8"))

    def _reply(self, code: int, text: str, content_type: str = "text/plain") -> None:
        body = text.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", f"{content_type}; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _reply_bytes(self, code: int, data: bytes, content_type: str) -> None:
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Cache-Control", "no-store")
        self.send_header("Connection", "keep-alive")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _read_body(self) -> str:
        try:
            length = int(self.headers.get("Content-Length", "0") or "0")
        except ValueError:
            return ""
        if length <= 0:
            return ""
        if length > 2 * 1024 * 1024:
            return ""
        return self.rfile.read(min(length, 2 * 1024 * 1024)).decode("utf-8", errors="replace")

    def do_GET(self):
        if not self._authorized():
            return self._reply(401, "unauthorized")
        parsed = urlparse(self.path)
        path = parsed.path

        if path == "/ping":
            return self._reply(200, "pong")

        # Проверка OTA-обновления для смартфона
        if path == "/update/check":
            info = get_latest_apk_info()
            if not info:
                return self._reply(
                    200,
                    json.dumps({"available": False, "reason": "no_apk_in_folder", "updatesDir": UPDATES_DIR}, ensure_ascii=False),
                    "application/json",
                )
            return self._reply(
                200,
                json.dumps(
                    {
                        "available": True,
                        "versionCode": info["versionCode"],
                        "versionName": info["versionName"],
                        "hash": info.get("hash", ""),
                        "size": info["size"],
                        "filename": info["filename"],
                        "updatesDir": UPDATES_DIR,
                    },
                    ensure_ascii=False,
                ),
                "application/json",
            )

        # Скачивание файла APK для обновления
        if path == "/update/download":
            info = get_latest_apk_info()
            if not info or not os.path.exists(info["path"]):
                return self._reply(404, "обновление не найдено")
            try:
                with open(info["path"], "rb") as f:
                    data = f.read()
                return self._reply_bytes(200, data, "application/vnd.android.package-archive")
            except Exception as e:
                return self._reply(500, f"ошибка чтения APK: {e}")

        if path == "/stream":
            qs = parse_qs(parsed.query)
            try:
                fps = int(qs.get("fps", ["60"])[0])
                fps = max(15, min(144, fps))
                w = int(qs.get("w", ["720"])[0])
                q = int(qs.get("q", ["48"])[0])
                all_mons = qs.get("m", ["0"])[0] == "all"
            except ValueError:
                fps, w, q, all_mons = 60, 720, 48, False

            self.send_response(200)
            self.send_header("Content-Type", "multipart/x-mixed-replace; boundary=frame")
            self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
            self.send_header("Pragma", "no-cache")
            self.send_header("Connection", "close")
            self.end_headers()

            try:
                self.connection.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            except Exception:
                pass

            frame_interval = 1.0 / fps
            try:
                while True:
                    t0 = time.monotonic()
                    jpg = capture_screen_jpeg(max_width=w, quality=q, all_monitors=all_mons)
                    if jpg:
                        header = f"--frame\r\nContent-Type: image/jpeg\r\nContent-Length: {len(jpg)}\r\n\r\n".encode("ascii")
                        self.wfile.write(header)
                        self.wfile.write(jpg)
                        self.wfile.write(b"\r\n")
                        self.wfile.flush()
                    elapsed = time.monotonic() - t0
                    sleep_rem = frame_interval - elapsed
                    if sleep_rem > 0.0005:
                        time.sleep(sleep_rem)
            except (ConnectionResetError, BrokenPipeError, socket.error):
                return

        if path == "/screen":
            qs = parse_qs(parsed.query)
            try:
                w = int(qs.get("w", ["960"])[0])
                q = int(qs.get("q", ["60"])[0])
                all_mons = qs.get("m", ["0"])[0] == "all"
            except ValueError:
                w, q, all_mons = 960, 60, False
            jpg = capture_screen_jpeg(max_width=w, quality=q, all_monitors=all_mons)
            if not jpg:
                return self._reply(500, "не удалось захватить экран")
            return self._reply_bytes(200, jpg, "image/jpeg")

        if path == "/clipboard":
            return self._reply(200, get_clipboard_text())

        if path == "/windows":
            return self._reply(
                200,
                json.dumps({"windows": list_open_windows()}, ensure_ascii=False),
                "application/json",
            )

        if path == "/apps":
            running = running_processes()
            apps = []
            for a in APP_LIST:
                proc = a.get("process")
                apps.append(
                    {
                        "id": a["id"],
                        "title": a["title"],
                        "canClose": bool(proc),
                        "running": bool(proc) and proc.lower() in running,
                    }
                )
            rem_sec = max(0, int(_shutdown_deadline - time.time())) if _shutdown_deadline > time.time() else 0
            ram_data = get_ram_info()
            payload = {
                "apps": apps,
                "activeWindow": get_active_window_title(),
                "ramPercent": ram_data["percent"],
                "ramTotalGb": ram_data["totalGb"],
                "ramUsedGb": ram_data["usedGb"],
                "cpuPercent": get_cpu_usage_percent(),
                "uptimeSec": get_system_uptime_seconds(),
                "disks": get_system_disks(),
                "shutdownRemainingSec": rem_sec,
                "shutdownActive": bool(rem_sec > 0),
                "volume": get_real_windows_volume(),
                "tunnelUrl": _cloudflare_url,
            }
            return self._reply(
                200,
                json.dumps(payload, ensure_ascii=False),
                "application/json",
            )
        if path == "/files/browse":
            qs = parse_qs(parsed.query)
            target = qs.get("path", [""])[0].strip()
            if not target or target == "/":
                target = "D:\\" if (IS_WINDOWS and os.path.exists("D:\\")) else ("C:\\" if IS_WINDOWS else "/")

            if not os.path.exists(target):
                target = "C:\\" if IS_WINDOWS else "/"

            target = os.path.abspath(target)
            parent = os.path.dirname(target.rstrip("\\/")) if target not in ("C:\\", "D:\\", "E:\\", "/") else ""

            drives = []
            if IS_WINDOWS:
                for letter in ["C:", "D:", "E:", "F:"]:
                    if os.path.exists(letter + "\\"):
                        drives.append(letter)
            else:
                drives = ["/"]

            entries = []
            try:
                with os.scandir(target) as it:
                    for entry in it:
                        try:
                            if entry.name.startswith("$") or entry.name in ("System Volume Information", "pagefile.sys", "hiberfil.sys", "swapfile.sys"):
                                continue
                            stat = entry.stat(follow_symlinks=False)
                            is_dir = entry.is_dir(follow_symlinks=False)
                            entries.append({
                                "name": entry.name,
                                "isDir": is_dir,
                                "size": stat.st_size if not is_dir else 0,
                                "mtime": int(stat.st_mtime)
                            })
                        except Exception:
                            continue
            except PermissionError:
                return self._reply(403, "нет доступа к папке")
            except Exception as e:
                return self._reply(500, f"ошибка чтения папки: {e}")

            entries.sort(key=lambda x: (not x["isDir"], x["name"].lower()))

            return self._reply(200, json.dumps({
                "path": target,
                "parent": parent,
                "drives": drives,
                "entries": entries[:250]
            }, ensure_ascii=False), "application/json")

        if path == "/files/download":
            qs = parse_qs(parsed.query)
            file_path = qs.get("path", [""])[0].strip()
            if not file_path or not os.path.isfile(file_path):
                return self._reply(404, "файл не найден")
            try:
                file_size = os.path.getsize(file_path)
                filename = os.path.basename(file_path)
                encoded_name = urllib.parse.quote(filename)
                self.send_response(200)
                self.send_header("Content-Type", "application/octet-stream")
                self.send_header("Content-Disposition", f'attachment; filename="{encoded_name}"')
                self.send_header("Content-Length", str(file_size))
                self.end_headers()
                with open(file_path, "rb") as f:
                    shutil.copyfileobj(f, self.wfile, length=65536)
                return
            except Exception as e:
                return self._reply(500, f"ошибка скачивания: {e}")

        self._reply(404, "not found")

    def do_POST(self):
        if not self._authorized():
            return self._reply(401, "unauthorized")
        path = urlparse(self.path).path

        # Загрузка фото со смартфона напрямую в папку photos (диск D:)
        if path == "/upload/media":
            raw_filename = self.headers.get("X-Filename", "")
            try:
                filename = urllib.parse.unquote_plus(raw_filename)
            except Exception:
                filename = raw_filename
            filename = os.path.basename(filename).strip()
            if not filename:
                filename = f"photo_{int(time.time())}.jpg"

            # Поддержка кириллицы и латиницы в названии фото
            clean_name = "".join(c for c in filename if c.isalnum() or c in "._- ()").strip()
            if not clean_name:
                clean_name = f"photo_{int(time.time())}.jpg"

            os.makedirs(MEDIA_DIR, exist_ok=True)
            dest_path = os.path.join(MEDIA_DIR, clean_name)
            base_part, ext_part = os.path.splitext(clean_name)
            counter = 1
            while os.path.exists(dest_path):
                dest_path = os.path.join(MEDIA_DIR, f"{base_part}_{counter}{ext_part}")
                counter += 1

            is_chunked = "chunked" in self.headers.get("Transfer-Encoding", "").lower()
            try:
                content_length = int(self.headers.get("Content-Length", 0))
            except ValueError:
                content_length = 0

            temp_path = dest_path + f"_{os.getpid()}_{int(time.time()*1000)}.part"
            try:
                bytes_received = 0
                with open(temp_path, "wb") as out_f:
                    if is_chunked:
                        while True:
                            line = self.rfile.readline()
                            if not line:
                                break  # EOF / клиент оборвал соединение
                            line = line.strip()
                            if not line:
                                continue
                            try:
                                chunk_len = int(line, 16)
                            except ValueError:
                                break
                            if chunk_len == 0:
                                self.rfile.readline()
                                break
                            data = self.rfile.read(chunk_len)
                            out_f.write(data)
                            bytes_received += len(data)
                            self.rfile.readline()
                    else:
                        remaining = content_length
                        chunk_size = 65536
                        while remaining > 0:
                            chunk = self.rfile.read(min(remaining, chunk_size))
                            if not chunk:
                                break
                            out_f.write(chunk)
                            bytes_received += len(chunk)
                            remaining -= len(chunk)

                if content_length > 0 and bytes_received < content_length:
                    if os.path.exists(temp_path):
                        os.remove(temp_path)
                    return self._reply(400, "файл передан не полностью")

                if os.path.exists(temp_path):
                    if os.path.exists(dest_path):
                        try: os.remove(dest_path)
                        except Exception: pass
                    os.replace(temp_path, dest_path)

                file_size = os.path.getsize(dest_path)
                size_mb = round(file_size / (1024 * 1024), 2)
                log(f"📥 Новое фото сохранено на ПК: {os.path.basename(dest_path)} ({size_mb} МБ)")
                return self._reply(200, json.dumps({
                    "status": "ok",
                    "filename": os.path.basename(dest_path),
                    "size": file_size
                }), "application/json")
            except Exception as e:
                log(f"Ошибка сохранения фото: {e}")
                if os.path.exists(temp_path):
                    try: os.remove(temp_path)
                    except Exception: pass
                return self._reply(500, f"ошибка сохранения: {e}")

        body = self._read_body().strip()
        path = urlparse(self.path).path

        if path == "/apps/add":
            code, text = add_custom_app(body)
            log(f"apps/add: {code} {text}")
            return self._reply(code, text)

        parts = path.strip("/").split("/")
        if len(parts) == 3 and parts[0] == "apps" and parts[2] == "delete":
            code, text = delete_custom_app(parts[1])
            log(f"apps/delete {parts[1]}: {code} {text}")
            return self._reply(code, text)

        if path == "/mouse":
            code, text = handle_mouse(body)
            return self._reply(code, text)

        if path == "/type":
            code, text = handle_type_text(body)
            log(f"type ({len(body)} симв.): {code} {text}")
            return self._reply(code, text)

        if path == "/timer":
            try:
                data = json.loads(body)
                mins = int(data.get("minutes", 30))
                act = str(data.get("action", "shutdown"))
            except Exception:
                return self._reply(400, "некорректный JSON таймера")
            code, text = set_custom_timer(mins, act)
            log(f"timer ({mins}m, {act}): {code} {text}")
            return self._reply(code, text)

        if path == "/volume":
            code, text = set_exact_volume(body)
            log(f"volume ({body}%): {code} {text}")
            return self._reply(code, text)

        if path == "/windows":
            code, text = control_window(body)
            log(f"window action: {code} {text}")
            return self._reply(code, text)

        if path == "/clipboard":
            ok = set_clipboard_text(body)
            return self._reply(200 if ok else 500, "ok" if ok else "не удалось скопировать в буфер ПК")

        if path == "/monitor_off":
            code, text = turn_off_monitor()
            log(f"monitor_off: {code} {text}")
            return self._reply(code, text)

        if path == "/sleep":
            if not IS_WINDOWS:
                return self._reply(400, "сон поддерживается только на Windows")
            self._reply(200, "ok")
            log("Выполняю переход в режим сна")
            threading.Thread(target=sleep_pc, daemon=True).start()
            return

        if path == "/open-url":
            url = body
            if body.startswith("{"):
                try:
                    url = str(json.loads(body).get("url", "")).strip()
                except Exception:
                    pass
            code, text = open_url_on_pc(url)
            log(f"open-url ({url}): {code} {text}")
            return self._reply(code, text)

        power_entry = POWER.get(path)
        if power_entry is not None:
            if not IS_WINDOWS:
                return self._reply(400, "команды питания поддерживаются только на Windows")
            cmd, timer_sec = power_entry
            self._reply(200, "ok")
            run_power(cmd, timer_sec)
            return

        if len(parts) == 2 and parts[0] == "media":
            code, text = send_media_key(parts[1])
            log(f"media {parts[1]}: {code} {text}")
            return self._reply(code, text)

        if len(parts) == 3 and parts[0] == "apps" and parts[2] in ("launch", "close"):
            app = APPS.get(parts[1])
            if app is None:
                return self._reply(404, "нет такой программы")
            action = launch_app if parts[2] == "launch" else close_app
            code, text = action(app)
            log(f"{parts[2]} {app['id']}: {code} {text}")
            return self._reply(code, text)

        self._reply(404, "not found")

    def log_message(self, fmt, *args):
        if len(args) > 1 and str(args[1]) == "200":
            if self.command == "GET" and (
                self.path.startswith("/apps")
                or self.path.startswith("/screen")
                or self.path.startswith("/windows")
                or self.path.startswith("/update")
            ):
                return
            if self.command == "POST" and self.path.startswith("/mouse"):
                return
        log(f"{self.client_address[0]} - {fmt % args}")


def check_paths() -> None:
    for a in APP_LIST:
        exe = os.path.expandvars(a["launch"][0])
        if not os.path.exists(exe):
            log(f"[!] {a['title']}: не найден файл {exe}. Поправь путь в config.json")


def _watch_server_file_and_restart():
    """Отслеживает изменение файла server.py и автоматически перезапускает процесс."""
    script_path = os.path.abspath(__file__)
    if not os.path.exists(script_path):
        return
    try:
        initial_mtime = os.path.getmtime(script_path)
    except OSError:
        return

    while True:
        time.sleep(1.5)
        try:
            if not os.path.exists(script_path):
                continue
            current_mtime = os.path.getmtime(script_path)
            if current_mtime != initial_mtime:
                import py_compile
                try:
                    py_compile.compile(script_path, doraise=True)
                except Exception as ce:
                    log(f"[!] Файл обновлён с синтаксической ошибкой: {ce}. Перезапуск отложен.")
                    initial_mtime = current_mtime
                    continue

                log("Файл server.py был обновлён на диске! Автоматический перезапуск сервера...")
                time.sleep(0.5)
                py_exe = sys.executable
                cmd = [py_exe, script_path] + sys.argv[1:]
                subprocess.Popen(cmd, cwd=BASE_DIR, creationflags=DETACHED_FLAGS)
                os._exit(0)
        except Exception:
            pass


def get_local_ips() -> list:
    ips = []
    try:
        _, _, host_ips = socket.gethostbyname_ex(socket.gethostname())
        for ip in host_ips:
            if not ip.startswith("127."):
                ips.append(ip)
    except Exception:
        pass
    return ips



_cloudflare_url = ""
_cloudflare_proc = None

def find_cloudflared_path():
    """Ищет исполняемый файл cloudflared в системе (PATH, папки установки MSI, Downloads или папка проекта)."""
    cand = shutil.which("cloudflared") or shutil.which("cloudflared.exe")
    if cand and os.path.isfile(cand):
        return cand
    
    script_dir = os.path.dirname(os.path.abspath(__file__))
    candidates = [
        # Папка проекта
        os.path.join(script_dir, "cloudflared.exe"),
        os.path.join(script_dir, "cloudflared-windows-amd64.exe"),
        # Стандартные папки установки MSI (Cloudflare One)
        os.path.expandvars(r"%ProgramFiles%\Cloudflare\cloudflared\cloudflared.exe"),
        os.path.expandvars(r"%ProgramFiles(x86)%\Cloudflare\cloudflared\cloudflared.exe"),
        os.path.expandvars(r"%ProgramFiles%\Cloudflare\cloudflared.exe"),
        os.path.expandvars(r"%ProgramFiles(x86)%\Cloudflare\cloudflared.exe"),
        os.path.expandvars(r"%ProgramFiles%\cloudflared\cloudflared.exe"),
        os.path.expandvars(r"%ProgramFiles(x86)%\cloudflared\cloudflared.exe"),
        os.path.expandvars(r"%LOCALAPPDATA%\Programs\cloudflared\cloudflared.exe"),
        # Папка Загрузки пользователя (если скачан Executable)
        os.path.expandvars(r"%USERPROFILE%\Downloads\cloudflared.exe"),
        os.path.expandvars(r"%USERPROFILE%\Downloads\cloudflared-windows-amd64.exe"),
    ]
    for p in candidates:
        if os.path.isfile(p):
            return p

    # Рекурсивный поиск в Program Files\Cloudflare, если установлен в подпапку
    for base in [os.path.expandvars(r"%ProgramFiles%\Cloudflare"), os.path.expandvars(r"%ProgramFiles(x86)%\Cloudflare")]:
        if os.path.isdir(base):
            try:
                for root, _, files in os.walk(base):
                    for f in files:
                        if f.lower() in ("cloudflared.exe", "cloudflared-windows-amd64.exe"):
                            return os.path.join(root, f)
            except Exception:
                pass

    return None

def _cloudflared_worker():
    global _cloudflare_url, _cloudflare_proc
    cf_path = find_cloudflared_path()
    if not cf_path:
        print("\n" + "-" * 60)
        print("  [!] Cloudflare Tunnel: cloudflared.exe пока не найден.")
        print("      Если вы установили MSI или скачали Executable,")
        print("      просто скопируйте cloudflared.exe в эту папку рядом с server.py!")
        print("-" * 60 + "\n")
        log("[Cloudflare Tunnel] cloudflared.exe не найден.")
        return

    log(f"[Cloudflare Tunnel] Найден cloudflared: {cf_path}")
    log(f"[Cloudflare Tunnel] Запуск автоматического интернет-туннеля для порта {PORT}…")

    url_regex = re.compile(r"https://[a-zA-Z0-9-]+\.trycloudflare\.com")
    while True:
        try:
            cmd = [cf_path, "tunnel", "--url", f"http://127.0.0.1:{PORT}"]
            creationflags = 0x08000000 if IS_WINDOWS else 0
            _cloudflare_proc = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                encoding="utf-8",
                errors="ignore",
                bufsize=1,
                creationflags=creationflags
            )

            for line in _cloudflare_proc.stdout:
                match = url_regex.search(line)
                if match:
                    found_url = match.group(0)
                    if found_url != _cloudflare_url:
                        _cloudflare_url = found_url
                        try:
                            with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "tunnel_url.txt"), "w", encoding="utf-8") as f:
                                f.write(found_url + "\n")
                        except Exception:
                            pass
                        print("\n" + "=" * 70)
                        log(f"[Cloudflare Tunnel] Внешний интернет-адрес: {found_url}")
                        print("  🌐 ВНЕШНИЙ ИНТЕРНЕТ-ТУННЕЛЬ АКТИВЕН (Cloudflare Tunnel):")
                        print("  Адрес для телефона (4G/5G / Вне дома):")
                        print(f"  -> {found_url}")
                        print("  Вставьте этот адрес в приложении на смартфоне в поле 'Адрес ПК'.")
                        print("  Tailscale и сторонние VPN на телефоне больше НЕ НУЖНЫ!")
                        print("=" * 70 + "\n")

            _cloudflare_proc.wait()
        except Exception as e:
            log(f"[Cloudflare Tunnel] Ошибка процесса: {e}")
        time.sleep(3)

def _udp_mouse_worker():
    """Быстрый UDP-сервер для мгновенного перемещения мыши (125+ Гц) с нулевой задержкой без накладных расходов HTTP/TCP."""
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(("0.0.0.0", PORT))
    except Exception as e:
        log(f"[UDP] Не удалось запустить UDP-сервер на порту {PORT}: {e}")
        return

    log(f"[UDP] Высокоскоростной мышиный UDP-сервер активен на порту {PORT}")
    while True:
        try:
            data, addr = sock.recvfrom(512)
            if not data:
                continue
            text = data.decode("utf-8", errors="ignore").strip()

            # Автопоиск ПК в локальной Wi-Fi сети без Tailscale
            if text == "PC_DISCOVERY_PING":
                pc_name = socket.gethostname() or "ПК"
                reply = f"PC_DISCOVERY_PONG:{pc_name}:{_cloudflare_url}"
                sock.sendto(reply.encode("utf-8"), addr)
                continue

            parts = text.split(":", 2)
            if len(parts) < 3:
                continue
            token_cand, cmd, args = parts[0], parts[1], parts[2]
            if not hmac.compare_digest(token_cand.encode("utf-8"), TOKEN.encode("utf-8")):
                continue

            # Мгновенное абсолютное следование курсора за пальцем (125+ Гц)
            if cmd == "abs":
                coords = args.split(",")
                if len(coords) == 2:
                    fnx = max(0.0, min(1.0, float(coords[0])))
                    fny = max(0.0, min(1.0, float(coords[1])))
                    ax = int(fnx * 65535)
                    ay = int(fny * 65535)
                    _send_inputs([_mouse_input(dx=ax, dy=ay, flags=MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE)])
            elif cmd == "scroll":
                try:
                    dy = int(args)
                    if dy:
                        _send_inputs([_mouse_input(data=dy * 120, flags=MOUSEEVENTF_WHEEL)])
                except Exception:
                    pass
            elif cmd == "m":
                coords = args.split(",")
                if len(coords) == 2:
                    dx = max(-2000, min(2000, int(coords[0])))
                    dy = max(-2000, min(2000, int(coords[1])))
                    if dx or dy:
                        _send_inputs([_mouse_input(dx=dx, dy=dy, flags=MOUSEEVENTF_MOVE)])
            elif cmd == "tap":
                coords = args.split(",")
                if len(coords) == 2:
                    fnx = max(0.0, min(1.0, float(coords[0])))
                    fny = max(0.0, min(1.0, float(coords[1])))
                    ax = int(fnx * 65535)
                    ay = int(fny * 65535)
                    _send_inputs([
                        _mouse_input(dx=ax, dy=ay, flags=MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE),
                        _mouse_input(flags=MOUSEEVENTF_LEFTDOWN),
                        _mouse_input(flags=MOUSEEVENTF_LEFTUP)
                    ])
            elif cmd == "act":
                if args == "left":
                    _send_inputs([_mouse_input(flags=MOUSEEVENTF_LEFTDOWN), _mouse_input(flags=MOUSEEVENTF_LEFTUP)])
                elif args == "right":
                    _send_inputs([_mouse_input(flags=MOUSEEVENTF_RIGHTDOWN), _mouse_input(flags=MOUSEEVENTF_RIGHTUP)])
                elif args == "double":
                    c = [_mouse_input(flags=MOUSEEVENTF_LEFTDOWN), _mouse_input(flags=MOUSEEVENTF_LEFTUP)]
                    _send_inputs(c + c)
        except Exception:
            pass


if __name__ == "__main__":
    if "--init" in sys.argv:
        ips = ", ".join(get_local_ips()) or "не определён"
        print(f"  IP-адреса ПК: {ips}")
        print(f"  Порт:         {PORT}")
        print(f"  Токен:        {TOKEN}")
        sys.exit(0)

    log(f"Токен: {TOKEN}", to_file=False)
    check_paths()
    log(f"Сервер слушает порт {PORT}. Остановить: Ctrl+C")
    threading.Thread(target=_watch_server_file_and_restart, daemon=True).start()
    threading.Thread(target=_udp_mouse_worker, daemon=True).start()
    threading.Thread(target=_cloudflared_worker, daemon=True).start()
    try:
        server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    except OSError as e:
        log(f"[!] Не удалось занять порт {PORT} (возможно, сервер уже запущен): {e}")
        raise SystemExit(1)
    server.serve_forever()