-- ThorSplit: the launcher runs WoW on one tall screen. The top part goes to the Thor's top display
-- and a band underneath goes to the bottom display. This addon keeps the 3D world and UIParent in the
-- top part and builds a panel host in the band, with a tab bar to toggle which panels are shown.
--
-- Other addons (the Thor Tukui layout) register panels with ThorSplit:RegisterPanel(...).

local ADDON = ...
local L = ThorSplitLayout
ThorSplit = ThorSplit or {}
local TS = ThorSplit
TS.panels, TS.order = {}, {}

if not L or not L.enabled then
    -- Split is off for this launch: register panels as no-ops so other addons don't need to care.
    function TS:RegisterPanel() end
    TS.active = false
    return
end
TS.active = true

local TAB_H = 72          -- tab bar height, in screen pixels
local GAP = 6

-- Screen pixels -> offsets for a frame anchored to the screen at its effective scale.
local function px(frame, value)
    return value * (768 / L.screenH) / frame:GetEffectiveScale()
end

------------------------------------------------------------------------
-- Top screen: keep the world and UIParent inside the top rectangle
------------------------------------------------------------------------
local placing, pendingPlace = false, false

local function PlaceTop()
    if InCombatLockdown() then pendingPlace = true; return end
    if placing then return end
    placing = true
    UIParent:ClearAllPoints()
    UIParent:SetPoint("TOPLEFT", nil, "TOPLEFT", 0, 0)
    UIParent:SetPoint("BOTTOMRIGHT", nil, "TOPLEFT", px(UIParent, L.topW), -px(UIParent, L.topH))
    WorldFrame:ClearAllPoints()
    WorldFrame:SetPoint("TOPLEFT", nil, "TOPLEFT", 0, 0)
    WorldFrame:SetPoint("BOTTOMRIGHT", nil, "TOPLEFT", px(WorldFrame, L.topW), -px(WorldFrame, L.topH))
    placing = false
    pendingPlace = false
    if TS.band then TS:PlaceBand() end
end
TS.PlaceTop = PlaceTop

------------------------------------------------------------------------
-- Bottom screen: the band
------------------------------------------------------------------------
local hidden = CreateFrame("Frame", "ThorSplitHidden")
hidden:Hide()

local band = CreateFrame("Frame", "ThorSplitBand", nil, BackdropTemplateMixin and "BackdropTemplate" or nil)
TS.band = band
band:SetFrameStrata("BACKGROUND")
band:SetScale(1)
band:SetClampedToScreen(false)
local bg = band:CreateTexture(nil, "BACKGROUND")
bg:SetAllPoints()
bg:SetColorTexture(0.04, 0.04, 0.05, 1)

local tabBar = CreateFrame("Frame", nil, band)
local content = CreateFrame("Frame", nil, band)
TS.content = content

function TS:PlaceBand()
    band:ClearAllPoints()
    band:SetPoint("TOPLEFT", nil, "TOPLEFT", px(band, L.bandX), -px(band, L.bandY))
    band:SetSize(px(band, L.bandW), px(band, L.bandH))
    tabBar:ClearAllPoints()
    tabBar:SetPoint("TOPLEFT", band, "TOPLEFT", 0, 0)
    tabBar:SetPoint("TOPRIGHT", band, "TOPRIGHT", 0, 0)
    tabBar:SetHeight(px(band, TAB_H))
    content:ClearAllPoints()
    content:SetPoint("TOPLEFT", tabBar, "BOTTOMLEFT", 0, -px(band, GAP))
    content:SetPoint("BOTTOMRIGHT", band, "BOTTOMRIGHT", 0, 0)
    TS:Layout()
end

local tabs = {}

local function StyleTab(tab, on)
    tab.bg:SetColorTexture(on and 0.16 or 0.08, on and 0.42 or 0.08, on and 0.55 or 0.1, on and 0.95 or 0.9)
    tab.text:SetTextColor(on and 1 or 0.6, on and 1 or 0.6, on and 1 or 0.6)
end

local function LayoutTabs()
    local n = #TS.order
    if n == 0 then return end
    local w = (L.bandW - GAP * (n + 1)) / n
    for i, id in ipairs(TS.order) do
        local tab = tabs[id]
        tab:ClearAllPoints()
        tab:SetPoint("TOPLEFT", tabBar, "TOPLEFT", px(band, GAP + (i - 1) * (w + GAP)), -px(band, GAP))
        tab:SetSize(px(band, w), px(band, TAB_H - GAP))
        StyleTab(tab, TS:IsShown(id))
    end
end

local function CreateTab(id, label)
    local tab = CreateFrame("Button", nil, tabBar)
    tab.bg = tab:CreateTexture(nil, "BACKGROUND")
    tab.bg:SetAllPoints()
    tab.text = tab:CreateFontString(nil, "OVERLAY", "GameFontNormalLarge")
    tab.text:SetPoint("CENTER")
    tab.text:SetText(label)
    tab:RegisterForClicks("AnyUp")
    tab:SetScript("OnClick", function() TS:Toggle(id) end)
    tabs[id] = tab
    return tab
end

------------------------------------------------------------------------
-- Panels
------------------------------------------------------------------------
-- def = { label = "Chat", order = 10, attach = function(cell) end, detach = function(holder) end }
function TS:RegisterPanel(id, def)
    if self.panels[id] then return end
    def.cell = CreateFrame("Frame", nil, content)
    self.panels[id] = def
    table.insert(self.order, id)
    table.sort(self.order, function(a, b) return (self.panels[a].order or 50) < (self.panels[b].order or 50) end)
    CreateTab(id, def.label)
    if self.ready then self:Layout() end
end

function TS:IsShown(id)
    return ThorSplitDB and ThorSplitDB.shown and ThorSplitDB.shown[id] == true
end

function TS:Toggle(id)
    if InCombatLockdown() and self.panels[id] and self.panels[id].protected then
        UIErrorsFrame:AddMessage("ThorSplit: can't change that panel in combat", 1, 0.3, 0.3)
        return
    end
    ThorSplitDB.shown[id] = not self:IsShown(id) or nil
    self:Layout()
end

-- Shown panels share the content area: 1 = full, 2 = side by side, 3-4 = 2x2, more = 3 columns.
function TS:Layout()
    if not self.ready then return end
    LayoutTabs()
    local shown = {}
    for _, id in ipairs(self.order) do
        if self:IsShown(id) then table.insert(shown, id) end
    end
    local n = #shown
    local cols = n <= 1 and 1 or (n == 2 and 2 or (n <= 4 and 2 or 3))
    local rows = math.max(1, math.ceil(n / cols))
    local cw = (L.bandW - GAP * (cols + 1)) / cols
    local ch = (L.bandH - TAB_H - GAP * (rows + 1)) / rows
    for _, id in ipairs(self.order) do
        local p = self.panels[id]
        if not self:IsShown(id) and p.attached then
            p.cell:Hide()
            if p.detach then p.detach(hidden) end
            p.attached = false
        end
    end
    for i, id in ipairs(shown) do
        local p = self.panels[id]
        local c, r = (i - 1) % cols, math.floor((i - 1) / cols)
        p.cell:ClearAllPoints()
        p.cell:SetPoint("TOPLEFT", content, "TOPLEFT", px(band, GAP + c * (cw + GAP)), -px(band, r * (ch + GAP)))
        p.cell:SetSize(px(band, cw), px(band, ch))
        p.cell:Show()
        if not p.cellBg then
            p.cellBg = p.cell:CreateTexture(nil, "BACKGROUND")
            p.cellBg:SetAllPoints()
            p.cellBg:SetColorTexture(0.07, 0.07, 0.08, 0.95)
        end
        if p.attach then p.attach(p.cell) end
        p.attached = true
    end
end

-- Helper for panels that move an existing frame into their cell and keep it there.
function TS.Dock(frame, cell, opts)
    if not frame then return end
    opts = opts or {}
    local wasPlacing = frame.thorSplitPlacing
    frame.thorSplitPlacing = true
    frame:SetParent(cell)
    frame:ClearAllPoints()
    local inset = px(band, opts.inset or 8)
    if opts.square then
        local size = math.min(cell:GetWidth(), cell:GetHeight()) - inset * 2
        frame:SetSize(size, size)
        frame:SetPoint("CENTER", cell, "CENTER")
    else
        frame:SetPoint("TOPLEFT", cell, "TOPLEFT", inset, -inset)
        frame:SetPoint("BOTTOMRIGHT", cell, "BOTTOMRIGHT", -inset, inset)
    end
    frame:Show()
    frame.thorSplitPlacing = wasPlacing
    -- Other code (chat, the tracker) likes to re-anchor these frames; put them back.
    if not frame.thorSplitHooked then
        frame.thorSplitHooked = true
        hooksecurefunc(frame, "SetPoint", function(f)
            if f.thorSplitPlacing or not f.thorSplitCell or InCombatLockdown() then return end
            if f:GetParent() ~= f.thorSplitCell then return end
            TS.Dock(f, f.thorSplitCell, f.thorSplitOpts)
        end)
    end
    frame.thorSplitCell, frame.thorSplitOpts = cell, opts
end

function TS.Undock(frame, holder)
    if not frame then return end
    frame.thorSplitCell = nil
    frame:SetParent(holder)
end

------------------------------------------------------------------------
-- Built-in panels (plain Blizzard frames). The Thor Tukui layout can replace these.
------------------------------------------------------------------------
local function first(...)
    for i = 1, select("#", ...) do
        local f = _G[(select(i, ...))]
        if f then return f end
    end
end

TS:RegisterPanel("quests", {
    label = "Quests", order = 10,
    attach = function(cell) TS.Dock(first("ObjectiveTrackerFrame", "QuestWatchFrame"), cell) end,
    detach = function(h) TS.Undock(first("ObjectiveTrackerFrame", "QuestWatchFrame"), h) end,
})
TS:RegisterPanel("chat", {
    label = "Chat", order = 20,
    attach = function(cell) TS.Dock(ChatFrame1, cell, { inset = 14 }) end,
    detach = function(h) TS.Undock(ChatFrame1, h) end,
})
TS:RegisterPanel("map", {
    label = "Map", order = 30,
    attach = function(cell) TS.Dock(Minimap, cell, { square = true }) end,
    detach = function(h) TS.Undock(Minimap, h) end,
})
TS:RegisterPanel("info", {
    label = "Info", order = 90,
    attach = function(cell)
        if not cell.info then
            cell.info = cell:CreateFontString(nil, "OVERLAY", "GameFontHighlightLarge")
            cell.info:SetPoint("CENTER")
            cell:SetScript("OnUpdate", function(self, el)
                self.t = (self.t or 0) + el
                if self.t < 0.5 then return end
                self.t = 0
                local fps = math.floor(GetFramerate() + 0.5)
                self.info:SetText(("ThorSplit active\n%dx%d screen, band %dx%d\n%s  |  %d fps")
                    :format(L.screenW, L.screenH, L.bandW, L.bandH, date("%H:%M:%S"), fps))
            end)
        end
    end,
})

------------------------------------------------------------------------
-- Startup
------------------------------------------------------------------------
local ev = CreateFrame("Frame")
ev:RegisterEvent("ADDON_LOADED")
ev:RegisterEvent("PLAYER_ENTERING_WORLD")
ev:RegisterEvent("DISPLAY_SIZE_CHANGED")
ev:RegisterEvent("UI_SCALE_CHANGED")
ev:RegisterEvent("PLAYER_REGEN_ENABLED")
ev:SetScript("OnEvent", function(_, event, arg)
    if event == "ADDON_LOADED" and arg == ADDON then
        ThorSplitDB = ThorSplitDB or {}
        ThorSplitDB.shown = ThorSplitDB.shown or { quests = true, info = true }
        hooksecurefunc(UIParent, "SetScale", PlaceTop)
        hooksecurefunc(UIParent, "SetPoint", function() if not placing then PlaceTop() end end)
        hooksecurefunc(WorldFrame, "SetPoint", function() if not placing then PlaceTop() end end)
        return
    end
    if event == "PLAYER_REGEN_ENABLED" then
        if pendingPlace then PlaceTop() end
        return
    end
    if event == "PLAYER_ENTERING_WORLD" then TS.ready = true end
    PlaceTop()
end)

SLASH_THORSPLIT1 = "/thorsplit"
SlashCmdList.THORSPLIT = function(msg)
    msg = (msg or ""):lower()
    if TS.panels[msg] then TS:Toggle(msg) return end
    print("|cff3fc7ebThorSplit|r panels: " .. table.concat(TS.order, ", ") .. "  (/thorsplit <panel> to toggle)")
end
