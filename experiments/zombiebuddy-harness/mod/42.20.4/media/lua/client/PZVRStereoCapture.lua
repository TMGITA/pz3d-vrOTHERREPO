-- Capture input is polled by the Java render-thread hook (Ctrl+Alt+Scroll Lock live mirror, Ctrl+Shift+F10 capture).
-- Do not register OnKeyPressed: PZ3D owns F8 and filters Lua input events.
local function maintainXR()
    if PZVRStereo then PZVRStereo.tickXR() end
end
Events.OnTickEvenPaused.Add(maintainXR)
Events.OnMainMenuEnter.Add(maintainXR)
