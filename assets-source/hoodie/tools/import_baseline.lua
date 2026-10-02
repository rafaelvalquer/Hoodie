-- Hoodie · importa um baseline (PNG + JSON do formato Aseprite) como um .aseprite
-- pronto para desenhar: paleta oficial, camadas do sprite master, tags, durações e
-- a camada "anchors" com 1 pixel por âncora em cada frame.
--
-- Uso pela interface: File › Scripts › Open Scripts Folder, copie este arquivo para
-- lá, depois File › Scripts › import_baseline e escolha um .json de baseline/.
--
-- Uso pela linha de comando:
--   aseprite -b --script-param json=assets-source/hoodie/baseline/hoodie_walk.json \
--            --script assets-source/hoodie/tools/import_baseline.lua
--
-- Saída: assets-source/hoodie/hoodie_walk.aseprite (ao lado da pasta baseline/).
-- Requer Aseprite 1.3+ (json.decode e Image{ fromFile }).

local PALETTE = {
  0x1A1C33, -- outline
  0x86A9E8, 0x6586CE, 0xB0C9F6, 0x4E69B0, -- fur, fur_shadow, fur_light, inner_ear
  0xB9CBEF, 0x92A9DB, 0x6E84BE, 0xDAE5FA, -- hoodie, hoodie_shadow, hoodie_dark, hoodie_light
  0x0F1124, 0xFFFFFF, 0x283063, 0xE07A93, 0xEFA3C8, -- eye, white, nose, tongue, blush
  0xF1F5FF, 0xDB7A3E, 0xAA5329, -- strings, backpack, backpack_dark
}

-- Mesma ordem do sprite master (de baixo para cima).
local LAYERS = { "legs", "fur", "fur_shadow", "hoodie", "hoodie_shadow", "strings", "arms", "ears", "face", "outline", "accessory" }

-- Cores da camada anchors (iguais a AnchorMarkers no código do app).
local ANCHOR_COLORS = {
  feet = Color{ r = 255, g = 0, b = 255 },
  head = Color{ r = 255, g = 255, b = 0 },
  right_hand = Color{ r = 255, g = 0, b = 0 },
  left_hand = Color{ r = 0, g = 0, b = 255 },
  back = Color{ r = 0, g = 255, b = 0 },
}

local function rgb(c) return Color{ r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF } end

local jsonPath = app.params["json"]
if not jsonPath or jsonPath == "" then
  local dlg = Dialog("Hoodie · importar baseline")
  dlg:file{ id = "json", label = "Baseline (.json)", open = true, filetypes = { "json" } }
  dlg:button{ id = "ok", text = "Importar" }
  dlg:button{ id = "cancel", text = "Cancelar" }
  dlg:show()
  if not dlg.data.ok then return end
  jsonPath = dlg.data.json
end
if not jsonPath or jsonPath == "" then return end

local f = assert(io.open(jsonPath, "r"), "não abri " .. jsonPath)
local data = json.decode(f:read("a"))
f:close()

local sheet = Image{ fromFile = (jsonPath:gsub("%.json$", ".png")) }
local frames = data.frames
local W, H = frames[1].frame.w, frames[1].frame.h

local spr = Sprite(W, H, ColorMode.RGB)
app.transaction("Importar baseline do Hoodie", function()
  local pal = Palette(#PALETTE + 1)
  pal:setColor(0, Color{ r = 0, g = 0, b = 0, a = 0 })
  for i, c in ipairs(PALETTE) do pal:setColor(i, rgb(c)) end
  spr:setPalette(pal)

  for _ = 2, #frames do spr:newEmptyFrame() end
  for i, fr in ipairs(frames) do spr.frames[i].duration = (fr.duration or 100) / 1000 end

  -- Referência: o desenho procedural, semi-transparente e travado.
  local ref = spr.layers[1]
  ref.name = "baseline (referencia)"
  for i, fr in ipairs(frames) do
    local img = Image(W, H, ColorMode.RGB)
    img:drawImage(sheet, Point(-fr.frame.x, -fr.frame.y))
    spr:newCel(ref, i, img, Point(0, 0))
  end
  ref.opacity = 96
  ref.isEditable = false

  for _, name in ipairs(LAYERS) do
    local layer = spr:newLayer()
    layer.name = name
  end

  -- Âncoras: um pixel por âncora por frame, a partir dos slices do baseline.
  local anchors = spr:newLayer()
  anchors.name = "anchors"
  for _, s in ipairs(data.meta.slices or {}) do
    local color = ANCHOR_COLORS[s.name]
    if color then
      for _, k in ipairs(s.keys or {}) do
        local i = k.frame + 1
        local cel = anchors:cel(i) or spr:newCel(anchors, i, Image(W, H, ColorMode.RGB), Point(0, 0))
        local px = k.bounds.x + ((k.pivot and k.pivot.x) or 0)
        local py = k.bounds.y + ((k.pivot and k.pivot.y) or 0)
        cel.image:drawPixel(px, py, color)
      end
    end
  end

  for _, t in ipairs(data.meta.frameTags or {}) do
    local tag = spr:newTag(t.from + 1, t.to + 1)
    tag.name = t.name
  end
end)

local out = jsonPath:gsub("baseline[/\\]", ""):gsub("%.json$", ".aseprite")
spr:saveAs(out)
print("Hoodie: salvo " .. out)
