$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$secureKey = Read-Host '请输入自己的 DeepSeek API Key（输入内容隐藏，仅用于本次后端进程）' -AsSecureString
$keyPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
$previousKey = $env:AI_API_KEY
try {
    $env:AI_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPointer).Trim()
    if ([string]::IsNullOrWhiteSpace($env:AI_API_KEY)) { throw 'API Key 不能为空' }
    Write-Host '正在启动后端。请先停止已占用8080端口的旧后端。请勿关闭本窗口。'
    & "$PSScriptRoot/mvnw.cmd" spring-boot:run
} finally {
    $env:AI_API_KEY = $previousKey
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPointer)
    $secureKey.Dispose()
}
