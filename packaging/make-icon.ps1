# Dessine l'icône du prototype (monogramme « P » jaune, police Kanit, fond sombre) : PNG pour la fenêtre,
# .ico pour l'exécutable. Icône originale : elle ne reprend pas le logo de PokeIsland.
# Usage : powershell -File packaging\make-icon.ps1   (depuis la racine du projet)
Add-Type -AssemblyName System.Drawing

$root = Split-Path $PSScriptRoot
$font = Join-Path $root "src\main\resources\fr\launcher\assets\fonts\Kanit-Black.ttf"
$pngDir = Join-Path $root "src\main\resources\fr\launcher\assets\icons"
$ico = Join-Path $PSScriptRoot "pokeisland.ico"
New-Item -ItemType Directory -Force $pngDir | Out-Null

$fonts = New-Object System.Drawing.Text.PrivateFontCollection
$fonts.AddFontFile($font)
$family = $fonts.Families[0]

function Draw-Icon([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $g.Clear([System.Drawing.Color]::Transparent)

    # Carré arrondi sombre (#0b0d12) avec un fin liseré jaune.
    $r = [single]($size * 0.22)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $s = [single]($size - 1)
    $path.AddArc(0, 0, $r * 2, $r * 2, 180, 90)
    $path.AddArc($s - $r * 2, 0, $r * 2, $r * 2, 270, 90)
    $path.AddArc($s - $r * 2, $s - $r * 2, $r * 2, $r * 2, 0, 90)
    $path.AddArc(0, $s - $r * 2, $r * 2, $r * 2, 90, 90)
    $path.CloseFigure()
    $g.FillPath((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 11, 13, 18))), $path)
    if ($size -ge 32) {
        $pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(110, 255, 210, 54)), ([single]($size / 40))
        $g.DrawPath($pen, $path)
    }

    # « P » jaune (#ffd236), centré sur sa forme réelle (et non sur la boîte de la police).
    $glyph = New-Object System.Drawing.Drawing2D.GraphicsPath
    $glyph.AddString("P", $family, [int][System.Drawing.FontStyle]::Regular, [single]($size * 0.78),
            (New-Object System.Drawing.PointF 0, 0), [System.Drawing.StringFormat]::GenericTypographic)
    $b = $glyph.GetBounds()
    $m = New-Object System.Drawing.Drawing2D.Matrix
    $m.Translate([single](($size - $b.Width) / 2 - $b.X), [single](($size - $b.Height) / 2 - $b.Y))
    $glyph.Transform($m)
    $g.FillPath((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 255, 210, 54))), $glyph)
    $g.Dispose()
    return $bmp
}

$sizes = 16, 32, 48, 64, 256
$pngs = @()
foreach ($size in $sizes) {
    $bmp = Draw-Icon $size
    $ms = New-Object System.IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $pngs += , $ms.ToArray()
    if ($size -ne 48) { $bmp.Save((Join-Path $pngDir "icon-$size.png"), [System.Drawing.Imaging.ImageFormat]::Png) }
    $bmp.Dispose()
}

# Fichier .ico : en-tête, une entrée par taille, puis les images PNG (format accepté depuis Windows Vista).
$out = New-Object System.IO.MemoryStream
$w = New-Object System.IO.BinaryWriter $out
$w.Write([uint16]0); $w.Write([uint16]1); $w.Write([uint16]$sizes.Count)
$offset = 6 + 16 * $sizes.Count
for ($i = 0; $i -lt $sizes.Count; $i++) {
    $dim = if ($sizes[$i] -ge 256) { 0 } else { $sizes[$i] }
    $w.Write([byte]$dim); $w.Write([byte]$dim); $w.Write([byte]0); $w.Write([byte]0)
    $w.Write([uint16]1); $w.Write([uint16]32)
    $w.Write([uint32]$pngs[$i].Length); $w.Write([uint32]$offset)
    $offset += $pngs[$i].Length
}
foreach ($png in $pngs) { $w.Write($png) }
[System.IO.File]::WriteAllBytes($ico, $out.ToArray())
Write-Output "Icône écrite : $ico et $pngDir"
