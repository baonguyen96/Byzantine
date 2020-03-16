for($i = 0; $i -le 4; $i++)
{
    Write-Host "Starting client $i..."
    $command = "-jar ..\Client.jar ..\Client\src\main\resources\Configurations\Client" + $i + "Configuration.txt"
    Start-Process -FilePath java -ArgumentList $command
}
