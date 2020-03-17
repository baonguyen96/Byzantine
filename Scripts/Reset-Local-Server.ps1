for($i = 0; $i -le 7; $i++)
{
    $path = "../Server/src/main/resources/Server$i"

    if((Test-Path -Path $path) -eq $true)
    {
        Write-Host "Clearing directory '$path'..."
        Get-ChildItem -Path $path -Force -Recurse | ForEach-Object { $_.Delete()}
        Remove-Item $path -Force
    }
}