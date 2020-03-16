for($i = 0; $i -le 7; $i++)
{
    $path = "../Server/src/main/resources/Server$i"
    Write-Host "Clearing directory '$path'..."
    Get-ChildItem -Path $path -File -Recurse | ForEach-Object { $_.Delete()}
}