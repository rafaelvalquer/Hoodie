# Exporta todos os .aseprite desta pasta para app/src/main/assets/pixel/hoodie/
# Requer o Aseprite no PATH (ou defina $env:ASEPRITE com o caminho do executável).
#
# Para cada arquivo saem três peças:
#   <nome>.png + <nome>.json   — o desenho (sem as camadas de referência e de âncoras)
#   <nome>.anchors.png         — só a camada "anchors" (1 pixel por âncora por frame)
$ErrorActionPreference = "Stop"
$aseprite = if ($env:ASEPRITE) { $env:ASEPRITE } else { "aseprite" }
$src = $PSScriptRoot
$out = Join-Path $src "..\..\app\src\main\assets\pixel\hoodie"
New-Item -ItemType Directory -Force $out | Out-Null

Get-ChildItem $src -Filter "hoodie_*.aseprite" | Where-Object { $_.BaseName -ne "hoodie_master" } | ForEach-Object {
    $name = $_.BaseName
    & $aseprite -b $_.FullName `
        --ignore-layer "baseline (referencia)" --ignore-layer "anchors" `
        --sheet (Join-Path $out "$name.png") `
        --data (Join-Path $out "$name.json") `
        --format json-array --list-tags --list-slices --sheet-type horizontal
    & $aseprite -b $_.FullName `
        --layer "anchors" `
        --sheet (Join-Path $out "$name.anchors.png") `
        --sheet-type horizontal
    Write-Host "exportado: $name"
}
