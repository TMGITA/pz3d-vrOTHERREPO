"""Add a movable Windows frame to SteamVR's simulated Headset Window only."""
import ctypes
from ctypes import wintypes as w
import json
from pathlib import Path

user = ctypes.WinDLL('user32', use_last_error=True)
kernel = ctypes.WinDLL('kernel32', use_last_error=True)
callback_type = ctypes.WINFUNCTYPE(w.BOOL, w.HWND, w.LPARAM)
user.EnumWindows.argtypes = [callback_type, w.LPARAM]
user.EnumWindows.restype = w.BOOL
user.GetWindowTextW.argtypes = [w.HWND, w.LPWSTR, ctypes.c_int]
user.GetWindowThreadProcessId.argtypes = [w.HWND, ctypes.POINTER(w.DWORD)]
user.GetWindowLongPtrW.argtypes = [w.HWND, ctypes.c_int]
user.GetWindowLongPtrW.restype = ctypes.c_ssize_t
user.SetWindowLongPtrW.argtypes = [w.HWND, ctypes.c_int, ctypes.c_ssize_t]
user.SetWindowLongPtrW.restype = ctypes.c_ssize_t
user.GetWindowRect.argtypes = [w.HWND, ctypes.POINTER(w.RECT)]
user.GetClientRect.argtypes = [w.HWND, ctypes.POINTER(w.RECT)]
user.GetDpiForWindow.argtypes = [w.HWND]
user.GetDpiForWindow.restype = w.UINT
user.AdjustWindowRectExForDpi.argtypes = [ctypes.POINTER(w.RECT), w.DWORD, w.BOOL, w.DWORD, w.UINT]
user.SetWindowPos.argtypes = [w.HWND, w.HWND, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_int, w.UINT]
user.SetThreadDpiAwarenessContext.argtypes = [w.HANDLE]
user.SetThreadDpiAwarenessContext.restype = w.HANDLE
kernel.OpenProcess.argtypes = [w.DWORD, w.BOOL, w.DWORD]
kernel.OpenProcess.restype = w.HANDLE
kernel.QueryFullProcessImageNameW.argtypes = [w.HANDLE, w.DWORD, w.LPWSTR, ctypes.POINTER(w.DWORD)]
kernel.CloseHandle.argtypes = [w.HANDLE]
FRAME = 0x00C00000 | 0x00080000 | 0x00020000  # Caption, system menu, minimize; preserve fixed client size.

def owner_path(pid):
    process = kernel.OpenProcess(0x1000, False, pid)
    if not process:
        return None
    try:
        name = ctypes.create_unicode_buffer(32768)
        size = w.DWORD(len(name))
        if kernel.QueryFullProcessImageNameW(process, 0, name, ctypes.byref(size)):
            return Path(name.value).resolve()
    finally:
        kernel.CloseHandle(process)

def checked(result):
    if not result:
        raise ctypes.WinError(ctypes.get_last_error())

def frame_headset_windows(runtime, apply=True):
    expected = (Path(runtime) / 'bin/win64/vrcompositor.exe').resolve()
    matches, failures = [], []
    @callback_type
    def inspect(hwnd, unused):
        try:
            title = ctypes.create_unicode_buffer(512)
            user.GetWindowTextW(hwnd, title, len(title))
            if title.value != 'Headset Window':
                return True
            pid = w.DWORD()
            user.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
            if owner_path(pid.value) != expected:
                return True
            before = user.GetWindowLongPtrW(hwnd, -16) & 0xFFFFFFFF
            desired = (before & ~0x80000000) | FRAME  # Replace popup frame with an ordinary caption.
            outer, client = w.RECT(), w.RECT()
            checked(user.GetWindowRect(hwnd, ctypes.byref(outer)))
            checked(user.GetClientRect(hwnd, ctypes.byref(client)))
            width, height = client.right, client.bottom
            changed = apply and before != desired
            if changed:
                ext = user.GetWindowLongPtrW(hwnd, -20) & 0xFFFFFFFF
                frame = w.RECT(0, 0, width, height)
                checked(user.AdjustWindowRectExForDpi(ctypes.byref(frame), desired, False, ext, user.GetDpiForWindow(hwnd)))
                ctypes.set_last_error(0)
                previous = user.SetWindowLongPtrW(hwnd, -16, desired)
                if not previous and ctypes.get_last_error():
                    raise ctypes.WinError(ctypes.get_last_error())
                # Keep the top-left and client extent, do not steal focus or change stacking.
                checked(user.SetWindowPos(hwnd, None, outer.left, outer.top, frame.right-frame.left,
                                          frame.bottom-frame.top, 0x0020 | 0x0004 | 0x0010))
            after = user.GetWindowLongPtrW(hwnd, -16) & 0xFFFFFFFF
            checked(user.GetClientRect(hwnd, ctypes.byref(client)))
            if apply and (after & FRAME != FRAME or after & 0x80000000):
                raise RuntimeError('Compositor did not retain requested window frame')
            matches.append(dict(title=title.value, pid=pid.value, hwnd=int(hwnd), changed=changed,
                                style=hex(after), clientBefore=[width,height], clientAfter=[client.right,client.bottom]))
        except Exception as error:
            failures.append(str(error))
        return True
    old_dpi = user.SetThreadDpiAwarenessContext(ctypes.c_void_p(-4))
    try:
        ctypes.set_last_error(0)
        if not user.EnumWindows(inspect, 0) and ctypes.get_last_error():
            raise ctypes.WinError(ctypes.get_last_error())
    finally:
        if old_dpi:
            user.SetThreadDpiAwarenessContext(old_dpi)
    if failures:
        raise RuntimeError('; '.join(failures))
    return matches

if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', default=r'C:\Program Files (x86)\Steam\steamapps\common\SteamVR')
    parser.add_argument('--inspect', action='store_true')
    args = parser.parse_args()
    found = frame_headset_windows(args.runtime, not args.inspect)
    print(json.dumps(found, indent=2) if found else 'No SteamVR Headset Window visible on this desktop.')
