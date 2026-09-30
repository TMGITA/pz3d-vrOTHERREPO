"""Keep the isolated SteamVR server alive as a non-scene OpenVR overlay client.

Uses SteamVR's installed API DLL; creates no overlay, window, XR session or game process.
OpenVR's Background application type intentionally does not keep the server alive.
"""
import ctypes
import sys
import time
from pathlib import Path
from HeadsetWindow import frame_headset_windows

runtime, run, server_pid = Path(sys.argv[1]), Path(sys.argv[2]), int(sys.argv[3])
kernel = ctypes.WinDLL('kernel32', use_last_error=True)
kernel.OpenProcess.argtypes = [ctypes.c_uint32, ctypes.c_int, ctypes.c_uint32]
kernel.OpenProcess.restype = ctypes.c_void_p
kernel.WaitForSingleObject.argtypes = [ctypes.c_void_p, ctypes.c_uint32]
kernel.CloseHandle.argtypes = [ctypes.c_void_p]
handle = kernel.OpenProcess(0x00100000, False, server_pid)  # SYNCHRONIZE only
if not handle:
    raise ctypes.WinError(ctypes.get_last_error())
api = ctypes.CDLL(str(runtime / 'bin/win64/openvr_api.dll'))
api.VR_InitInternal2.argtypes = [ctypes.POINTER(ctypes.c_int), ctypes.c_int, ctypes.c_char_p]
api.VR_InitInternal2.restype = ctypes.c_uint32
api.VR_ShutdownInternal.argtypes = []
api.VR_ShutdownInternal.restype = None
error = ctypes.c_int()
initialized = False
try:
    api.VR_InitInternal2(ctypes.byref(error), 2, None)  # VRApplication_Overlay; no scene ownership
    if error.value:
        raise RuntimeError(f'OpenVR keepalive initialization failed: {error.value}')
    initialized = True
    (run / 'keepalive.ready').write_text('Connected to simulated SteamVR\n')
    next_window_check = 0
    last_window_error = None
    while not (run / 'keepalive.stop').exists() and kernel.WaitForSingleObject(handle, 0) == 258:
        if time.monotonic() >= next_window_check:
            next_window_check = time.monotonic() + 1
            try:
                for window in frame_headset_windows(runtime):
                    if window['changed']:
                        print(f"Added draggable Headset Window frame: {window}", flush=True)
                last_window_error = None
            except Exception as failure:
                if str(failure) != last_window_error:
                    print(f'Headset Window frame unavailable: {failure}', flush=True)
                    last_window_error = str(failure)
        time.sleep(.25)
finally:
    if initialized:
        api.VR_ShutdownInternal()
    kernel.CloseHandle(handle)
