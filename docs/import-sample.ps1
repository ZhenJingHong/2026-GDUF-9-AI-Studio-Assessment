# Import sample bills into the local backend
$ErrorActionPreference = "Stop"
$csv = Import-Csv "docs\sample-bills.csv" -Encoding UTF8
$ok = 0
$fail = 0
foreach ($row in $csv) {
    $body = $row | ConvertTo-Json -Compress
    try {
        $resp = Invoke-WebRequest -Method Post -Uri "http://localhost:8080/api/bill" `
            -ContentType "application/json; charset=utf-8" `
            -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -UseBasicParsing
        $result = [System.Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray()) | ConvertFrom-Json
        if ($result.code -eq 0) { $ok++ } else { $fail++; Write-Host "FAIL: $($row.note) - $($result.message)" }
    } catch {
        $fail++
        Write-Host "FAIL: $($row.note) - $($_.Exception.Message)"
    }
}
Write-Host "Done: $ok succeeded, $fail failed"