"Building..."
Write-Host

$start = Get-Date
$thisDirectory = "$PSScriptRoot"

Set-Location ..
mvn clean install -U
mvn package

"Moving jar file..."

$modules = "Server", "Client"

ForEach($module in $modules)
{
    Move-Item -Path "./$module/target/$module-1.0-SNAPSHOT-jar-with-dependencies.jar" -Destination "./$module.jar" -Force
}

Set-Location $thisDirectory

$end = Get-Date
$duration = New-TimeSpan -Start $start -End $end

Write-Host
"Finished after " + $duration.Minutes + " minute(s) " + ($duration.Seconds % 60) + " second(s)"