# Generate the Windows vector-style mark using only Windows drawing primitives.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$assets = Join-Path $PSScriptRoot '../BluetoothDisable.App/Assets'
function Write-Mark([int]$width, [int]$height, [string]$name) {
    $bitmap = [Drawing.Bitmap]::new($width, $height)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
        $graphics.Clear([Drawing.Color]::FromArgb(37, 99, 235))
        $side = [Math]::Min($width, $height)
        $graphics.TranslateTransform(($width - $side) / 2, ($height - $side) / 2)
        $graphics.ScaleTransform($side / 100, $side / 100)
        $pen = [Drawing.Pen]::new([Drawing.Color]::White, 5)
        $pen.StartCap = $pen.EndCap = [Drawing.Drawing2D.LineCap]::Round
        $pen.LineJoin = [Drawing.Drawing2D.LineJoin]::Round
        try {
            $points = [Drawing.PointF[]]@([Drawing.PointF]::new(34, 34), [Drawing.PointF]::new(65, 65),
                [Drawing.PointF]::new(50, 80), [Drawing.PointF]::new(50, 20),
                [Drawing.PointF]::new(65, 35), [Drawing.PointF]::new(34, 66))
            $graphics.DrawLines($pen, $points)
        } finally { $pen.Dispose() }
        $bitmap.Save((Join-Path $assets $name), [Drawing.Imaging.ImageFormat]::Png)
    } finally { $graphics.Dispose(); $bitmap.Dispose() }
}
Write-Mark 300 300 'BluetoothSquare150.scale-200.png'
Write-Mark 88 88 'BluetoothSquare44.scale-200.png'
Write-Mark 48 48 'BluetoothSquare44.targetsize-48.png'
Write-Mark 100 100 'BluetoothStore.scale-200.png'
Write-Mark 620 300 'BluetoothWide.scale-200.png'
Write-Mark 1240 600 'BluetoothSplash.scale-200.png'
Write-Mark 256 256 'BluetoothIcon.png'
# ICO with a PNG-compressed 256px entry; modern Windows scales it for shortcuts/title bars.
$png = [IO.File]::ReadAllBytes((Join-Path $assets 'BluetoothIcon.png'))
$stream = [IO.File]::Create((Join-Path $assets 'AppIcon.ico'))
$writer = [IO.BinaryWriter]::new($stream)
try {
    $writer.Write([uint16]0); $writer.Write([uint16]1); $writer.Write([uint16]1)
    $writer.Write([byte]0); $writer.Write([byte]0); $writer.Write([byte]0); $writer.Write([byte]0)
    $writer.Write([uint16]1); $writer.Write([uint16]32)
    $writer.Write([uint32]$png.Length); $writer.Write([uint32]22); $writer.Write($png)
} finally { $writer.Dispose() }
