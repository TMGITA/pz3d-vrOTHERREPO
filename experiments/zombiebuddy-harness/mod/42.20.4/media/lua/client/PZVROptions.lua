require "PZAPI/ModOptions"

local options = PZAPI.ModOptions:create("PZ3DVRTest", "PZ3D VR")
options:addDescription("Choose a keyboard key and modifiers for each shortcut. Press the key alone in the picker, then select modifiers below. Use Clear in the picker to disable a shortcut. Changes take effect with Apply. Identical shortcuts are disabled until changed.")
local rows = {
    {"xr", "Toggle OpenXR", Keyboard.KEY_SCROLL, 3},
    {"recenter", "Recenter headset", Keyboard.KEY_SCROLL, 7},
    {"preview", "Toggle synthetic arms (XR) / desktop stereo (XR off)", Keyboard.KEY_SCROLL, 5},
    {"capture", "Save stereo PNG pair (XR off)", Keyboard.KEY_F10, 3},
}
local modifiers = {"None", "Ctrl", "Shift", "Ctrl + Shift", "Alt", "Ctrl + Alt", "Shift + Alt", "Ctrl + Shift + Alt"}
for _, row in ipairs(rows) do
    options:addKeyBind(row[1], row[2], row[3])
    local combo = options:addComboBox(row[1] .. "Modifiers", row[2] .. " - modifiers")
    for index, label in ipairs(modifiers) do combo:addItem(label, index == row[4] + 1) end
end
options:addDescription("Hold the selected modifiers before pressing the key. Extra modifiers do not match. Choose keys that do not conflict with your game or other mods; these shortcuts do not consume native controls.")
options:addSeparator()
options:addSlider("armReachPercent", "Maximum arm reach (%)", 100, 175, 5, 150)
options:addDescription("Arms extend only when needed to reach the tracked controllers, up to this percentage of the character's normal arm length. Hands and items keep their size. 100 restores the original reach limit. Higher limits can visibly stretch sleeves and elbows.")

local function sync()
    if not PZVRStereo or not PZVRStereo.setHotkeys then return end
    local values = {}
    for _, row in ipairs(rows) do
        table.insert(values, options:getOption(row[1]):getValue())
        table.insert(values, options:getOption(row[1] .. "Modifiers"):getValue() - 1)
    end
    PZVRStereo.setHotkeys(unpack(values))
    if PZVRStereo.setArmReachPercent then
        PZVRStereo.setArmReachPercent(math.floor(options:getOption("armReachPercent"):getValue() + 0.5))
    end
end
function options:apply()
    -- Vanilla's mod key picker edits its UI record separately from the saved option.
    for _, row in ipairs(rows) do
        local option = self:getOption(row[1])
        if option.element then option:setValue(option.element.keyCode) end
    end
    sync()
end
local nativeLoad = PZAPI.ModOptions.load
function PZAPI.ModOptions:load(...)
    nativeLoad(self, ...)
    sync()
end
Events.OnMainMenuEnter.Add(sync)
Events.OnGameStart.Add(sync)

local function guardSettings()
    if not PZVRStereo or not PZVRStereo.blockHotkeys then return end
    local visible = MainOptions and MainOptions.instance and MainOptions.instance:isVisible()
    PZVRStereo.blockHotkeys(visible == true or getCore():isDoingTextEntry())
end
Events.OnTickEvenPaused.Add(guardSettings)
Events.OnMainMenuEnter.Add(guardSettings)
