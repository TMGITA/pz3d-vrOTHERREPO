-- Remappable input is polled by the Java render-thread hook; see PZVROptions.lua.
-- Do not register OnKeyPressed: PZ3D owns F8 and filters Lua input events.
local function maintainXR()
    if PZVRStereo then PZVRStereo.tickXR() end
end
Events.OnTickEvenPaused.Add(maintainXR)
Events.OnMainMenuEnter.Add(maintainXR)

-- Draw into the vanilla UI, which is also presented as the VR UI panel.
local function drawRecenterStatus()
    if not PZVRStereo or not PZVRStereo.recenterStatus then return end
    local text = PZVRStereo.recenterStatus()
    if text and text ~= "" then
        getTextManager():DrawStringCentre(UIFont.Large, getCore():getScreenWidth() / 2 + 1, 81, text, 0, 0, 0, 1)
        getTextManager():DrawStringCentre(UIFont.Large, getCore():getScreenWidth() / 2, 80, text, 1, 1, 1, 1)
    end
end
Events.OnPostUIDraw.Add(drawRecenterStatus)
