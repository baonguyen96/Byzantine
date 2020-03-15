for($i = 1; $i -le 5; $i++)
{
    Write-Host "Starting client $i..."
    $command = "-jar ..\Client.jar ..\Client\src\main\resources\Configurations\Client" + $i + "Configuration.txt"
    Start-Process -FilePath java -ArgumentList $command
}
