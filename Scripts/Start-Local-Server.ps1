$thisDirectory = "$PSScriptRoot"

Set-Location ..

for($i = 0; $i -le 6; $i++)
{
    Write-Host "Starting server $i..."
    $command = "-jar .\Server.jar .\Server\src\main\resources\Configurations\Server" + $i + "Configuration.txt"
    Start-Process -FilePath java -ArgumentList $command
}

Set-Location $thisDirectory
