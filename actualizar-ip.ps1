# actualizar-ip.ps1
$propertiesPath = "C:\Users\danie\Documents\cyberpos-app\local.properties"

# Obtiene la IP de la interfaz Wi-Fi activa (ajusta el nombre si tu interfaz se llama distinto)
$ip = (Get-NetIPAddress -AddressFamily IPv4 | Where-Object {
    $_.InterfaceAlias -match "Wi-Fi|Wireless" -and $_.IPAddress -notmatch "^169\."
}).IPAddress | Select-Object -First 1

if (-not $ip) {
    Write-Host "No se encontró IP de Wi-Fi activa. Revisa la conexión." -ForegroundColor Red
    exit 1
}

Write-Host "IP detectada: $ip" -ForegroundColor Green

if (-not (Test-Path $propertiesPath)) {
    Write-Host "local.properties no existe. Creándolo..." -ForegroundColor Yellow
    @"
sdk.dir=C:\\Users\\danie\\AppData\\Local\\Android\\Sdk
BTCPAY_API_KEY=TU_API_KEY_AQUI
BTCPAY_STORE_ID=3f1HYtdwWuqELBu1yRGjQzB2SZL1fCcpBHVzV9DwZazp
BTCPAY_URL=http://${ip}:14142
"@ | Set-Content $propertiesPath
} else {
    $content = Get-Content $propertiesPath -Raw
    if ($content -match "BTCPAY_URL=.*") {
        $newContent = $content -replace "BTCPAY_URL=.*", "BTCPAY_URL=http://${ip}:14142"
        Set-Content -Path $propertiesPath -Value $newContent -NoNewline
        Write-Host "BTCPAY_URL actualizado a: http://${ip}:14142" -ForegroundColor Green
    } else {
        Add-Content -Path $propertiesPath -Value "BTCPAY_URL=http://${ip}:14142"
        Write-Host "BTCPAY_URL agregado: http://${ip}:14142" -ForegroundColor Green
    }
}

# Verifica que la regla de firewall siga activa
$rule = Get-NetFirewallRule -DisplayName "BTCPay" -ErrorAction SilentlyContinue
if (-not $rule) {
    Write-Host "Regla de firewall no encontrada. Creándola..." -ForegroundColor Yellow
    New-NetFirewallRule -DisplayName "BTCPay" -Direction Inbound -Protocol TCP -LocalPort 14142 -Action Allow
} else {
    Write-Host "Regla de firewall OK." -ForegroundColor Green
}

Write-Host "`nListo. Ahora sincroniza el proyecto en Android Studio (File > Sync Now)." -ForegroundColor Cyan